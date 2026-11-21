package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureLearningService;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Records actual B3 consumer receipts and separately verified downstream outcomes. A successful
 * broadcast/receipt alone never becomes a positive learning signal.
 */
public final class B3OutcomeEvidenceService implements WorkspaceReceiptRecorder {
    /** Evidence type whose ID is the SHA-256 of immutable scenario inputs, not an output workspace. */
    public static final String EXPERIMENT_UNIT_EVIDENCE_TYPE = "experiment-unit-sha256";
    private final B3OutcomeEvidenceRepository repository;
    private final DownstreamOutcomeEvidenceVerifier verifier;
    private final String admissionPolicyVersion;
    private final String verifierId;
    private final B1MemoryFeatureLearningService b1FeatureLearning;

    public B3OutcomeEvidenceService(B3OutcomeEvidenceRepository repository,
                                    DownstreamOutcomeEvidenceVerifier verifier,
                                    String admissionPolicyVersion, String verifierId) {
        this(repository, verifier, admissionPolicyVersion, verifierId, null);
    }

    /** Full integration: verified candidate outcomes update the exact attributed B1 memory revisions. */
    public B3OutcomeEvidenceService(B3OutcomeEvidenceRepository repository,
                                    DownstreamOutcomeEvidenceVerifier verifier,
                                    String admissionPolicyVersion, String verifierId,
                                    B1MemoryFeatureLearningService b1FeatureLearning) {
        this.repository=Objects.requireNonNull(repository,"repository");
        this.verifier=Objects.requireNonNull(verifier,"verifier");
        this.admissionPolicyVersion=required(admissionPolicyVersion,"admissionPolicyVersion");
        this.verifierId=required(verifierId,"verifierId");
        this.b1FeatureLearning=b1FeatureLearning;
    }

    @Override public void record(WorkspaceSnapshot snapshot,WorkspaceConsumerReceipt receipt) {
        Objects.requireNonNull(snapshot,"snapshot"); Objects.requireNonNull(receipt,"receipt");
        if(!snapshot.id().equals(receipt.workspaceId())) throw new SecurityException("receipt belongs to another workspace");
        var admitted=new TreeMap<String,WorkspaceCandidate>();
        snapshot.entries().forEach(e->admitted.put(e.candidate().id(),e.candidate()));
        var consumed=new TreeMap<String,String>();
        if(receipt.status()==ConsumerReceiptStatus.ACCEPTED) {
            for(String id:receipt.consumedCandidateIds()) {
                WorkspaceCandidate candidate=admitted.get(id);
                if(candidate==null) throw new SecurityException("receipt references candidate outside workspace");
                consumed.put(id,candidateFingerprint(candidate));
            }
        }
        String receiptId=receiptId(snapshot.id(),receipt.consumerId());
        var attributions = new TreeMap<String,List<dev.supplychain.agent.mechanisms.b1.B1MemoryAttribution>>();
        consumed.keySet().forEach(candidateId -> {
            List<dev.supplychain.agent.mechanisms.b1.B1MemoryAttribution> mapped = admitted.get(candidateId).memoryAttributions();
            if (!mapped.isEmpty()) attributions.put(candidateId, mapped);
        });
        var coalitions = new TreeMap<String,WorkspaceCoalitionReceipt>();
        if (receipt.status() == ConsumerReceiptStatus.ACCEPTED) {
            for (WorkspaceCoalitionProposal proposal : snapshot.evaluatedCoalitionProposals()) {
                if (!proposal.selected() || !consumed.keySet().containsAll(proposal.candidateIds())) continue;
                String coalitionId = "coalition:" + proposal.id();
                var memberFingerprints = new TreeMap<String,String>();
                proposal.candidateIds().forEach(id -> memberFingerprints.put(id, consumed.get(id)));
                coalitions.put(coalitionId, new WorkspaceCoalitionReceipt(coalitionId, snapshot.scopeId(), snapshot.id(),
                        proposal.round(), proposal.fingerprint(), proposal.preferenceKey(), Set.copyOf(proposal.candidateIds()), memberFingerprints,
                        proposal.newlyCoveredTags(), proposal.contextUnits(), snapshot.experimentUnitFingerprint()));
            }
        }
        WorkspaceReceiptEvidence evidence=new WorkspaceReceiptEvidence(receiptId,snapshot.scopeId(),snapshot.cycleId(),snapshot.id(),
                receipt.consumerId(),receipt.status(),consumed,workspaceFingerprint(snapshot),snapshot.experimentUnitFingerprint(),
                admissionPolicyVersion,receipt.receivedAt(),attributions,coalitions);
        repository.appendReceipt(evidence);
    }

    public DownstreamOutcomeEvidence recordVerifiedOutcome(DownstreamOutcomeReport report) {
        Objects.requireNonNull(report,"report");
        WorkspaceReceiptEvidence receipt=repository.findReceipt(report.scopeId(),report.receiptId())
                .orElseThrow(()->new SecurityException("downstream outcome has no persisted receipt"));
        if(receipt.status()!=ConsumerReceiptStatus.ACCEPTED) throw new SecurityException("only accepted receipts can support downstream outcomes");
        String fingerprint=receipt.consumedCandidateFingerprints().get(report.candidateId());
        if(fingerprint==null) throw new SecurityException("candidate was not consumed by this receipt");
        if(report.verifiedAt().isBefore(receipt.receivedAt())) throw new IllegalArgumentException("outcome predates downstream receipt");
        if(!verifier.verify(report.scopeId(),report.receiptId(),report.candidateId(),report.signal(),report.evidence(),report.verifiedAt()))
            throw new SecurityException("downstream outcome evidence could not be verified");
        DownstreamOutcomeEvidence outcome=new DownstreamOutcomeEvidence(report.idempotencyKey(),receipt.receiptId(),receipt.scopeId(),
                receipt.cycleId(),receipt.workspaceId(),report.candidateId(),fingerprint,receipt.consumerId(),report.signal(),
                verifierId,report.evidence(),receipt.experimentUnitFingerprint(),receipt.admissionPolicyVersion(),report.verifiedAt(),
                receipt.memoryAttributionsByCandidate().getOrDefault(report.candidateId(), List.of()));
        repository.appendOutcome(outcome);
        // Re-read through the capability boundary after durable append. Retries are harmless because
        // B1 feature updates are idempotent by verified outcome key.
        if (b1FeatureLearning != null) {
            verifiedOutcomes(report.scopeId(), 10000).stream()
                    .filter(value -> value.evidence().idempotencyKey().equals(report.idempotencyKey()))
                    .findFirst().ifPresent(b1FeatureLearning::applyVerifiedOutcome);
        }
        return outcome;
    }

    /** Raw audit view. Callers must not pass these publicly constructible records to the trainer. */
    public List<DownstreamOutcomeEvidence> outcomes(MemoryScopeId scopeId,int maximum) {
        return repository.findOutcomes(Objects.requireNonNull(scopeId,"scopeId"),maximum);
    }

    /**
     * Trainer input is revalidated at read time, not trusted merely because a row exists in the
     * repository. This detects stale, cross-linked, or manually inserted payloads before minting
     * the package-private verified-evidence capability.
     */
    public List<VerifiedDownstreamOutcomeEvidence> verifiedOutcomes(MemoryScopeId scopeId,int maximum) {
        Objects.requireNonNull(scopeId,"scopeId");
        return repository.findOutcomes(scopeId,maximum).stream().map(outcome -> {
            WorkspaceReceiptEvidence receipt=repository.findReceipt(scopeId,outcome.receiptId())
                    .orElseThrow(()->new SecurityException("persisted B3 outcome has no durable receipt"));
            String consumedFingerprint=receipt.consumedCandidateFingerprints().get(outcome.candidateId());
            if(receipt.status()!=ConsumerReceiptStatus.ACCEPTED
                    ||!receipt.scopeId().equals(outcome.scopeId())
                    ||!receipt.cycleId().equals(outcome.cycleId())
                    ||!receipt.workspaceId().equals(outcome.workspaceId())
                    ||!receipt.consumerId().equals(outcome.consumerId())
                    ||!receipt.admissionPolicyVersion().equals(outcome.admissionPolicyVersion())
                    ||!outcome.candidateFingerprint().equals(consumedFingerprint)
                    ||!outcome.memoryAttributions().equals(receipt.memoryAttributionsByCandidate().getOrDefault(outcome.candidateId(), List.of()))
                    ||!outcome.experimentUnitFingerprint().equals(receipt.experimentUnitFingerprint())
                    ||!outcome.experimentUnitFingerprint().matches("[0-9a-f]{64}")
                    ||!outcome.evidence().contains(new EvidenceReference(EXPERIMENT_UNIT_EVIDENCE_TYPE, outcome.experimentUnitFingerprint()))
                    ||outcome.verifiedAt().isBefore(receipt.receivedAt())
                    ||!verifierId.equals(outcome.verifierId()))
                throw new SecurityException("persisted B3 outcome no longer matches its exact accepted receipt/verifier");
            if(!verifier.verify(scopeId,outcome.receiptId(),outcome.candidateId(),outcome.signal(),outcome.evidence(),outcome.verifiedAt()))
                throw new SecurityException("persisted downstream outcome proof failed revalidation");
            return new VerifiedDownstreamOutcomeEvidence(outcome, outcome.experimentUnitFingerprint());
        }).peek(verified -> { if (b1FeatureLearning != null) b1FeatureLearning.applyVerifiedOutcome(verified); }).toList();
    }

    public WorkspaceCoalitionOutcomeEvidence recordVerifiedCoalitionOutcome(WorkspaceCoalitionOutcomeReport report) {
        Objects.requireNonNull(report, "report");
        WorkspaceReceiptEvidence receipt = repository.findReceipt(report.scopeId(), report.receiptId())
                .orElseThrow(() -> new SecurityException("coalition outcome has no persisted receipt"));
        if (receipt.status() != ConsumerReceiptStatus.ACCEPTED) throw new SecurityException("coalition outcome requires an accepted receipt");
        WorkspaceCoalitionReceipt coalition = receipt.selectedCoalitions().get(report.coalitionId());
        if (coalition == null) throw new SecurityException("coalition was not selected and consumed in this receipt");
        if (report.verifiedAt().isBefore(receipt.receivedAt())) throw new IllegalArgumentException("coalition outcome predates receipt");
        if (!verifier.verify(report.scopeId(), report.receiptId(), report.coalitionId(), report.signal(), report.evidence(), report.verifiedAt()))
            throw new SecurityException("coalition outcome evidence could not be verified");
        WorkspaceCoalitionOutcomeEvidence outcome = new WorkspaceCoalitionOutcomeEvidence(report.idempotencyKey(),
                receipt.receiptId(), receipt.scopeId(), receipt.workspaceId(), receipt.cycleId(), receipt.consumerId(),
                coalition.coalitionId(), coalition.proposalFingerprint(), coalition.preferenceKey(), coalition.candidateIds(), report.signal(),
                verifierId, report.evidence(), coalition.experimentUnitFingerprint(), receipt.admissionPolicyVersion(), report.verifiedAt());
        repository.appendCoalitionOutcome(outcome);
        return outcome;
    }

    public List<VerifiedWorkspaceCoalitionOutcomeEvidence> verifiedCoalitionOutcomes(MemoryScopeId scopeId,int maximum) {
        Objects.requireNonNull(scopeId,"scopeId");
        return repository.findCoalitionOutcomes(scopeId, maximum).stream().map(outcome -> {
            WorkspaceReceiptEvidence receipt=repository.findReceipt(scopeId,outcome.receiptId())
                    .orElseThrow(()->new SecurityException("persisted coalition outcome has no durable receipt"));
            WorkspaceCoalitionReceipt coalition=receipt.selectedCoalitions().get(outcome.coalitionId());
            if(receipt.status()!=ConsumerReceiptStatus.ACCEPTED || coalition==null
                    || !receipt.scopeId().equals(outcome.scopeId()) || !receipt.workspaceId().equals(outcome.workspaceId())
                    || !receipt.cycleId().equals(outcome.cycleId()) || !receipt.consumerId().equals(outcome.consumerId())
                    || !coalition.proposalFingerprint().equals(outcome.proposalFingerprint())
                    || !coalition.preferenceKey().equals(outcome.preferenceKey())
                    || !coalition.candidateIds().equals(outcome.candidateIds())
                    || !coalition.experimentUnitFingerprint().equals(outcome.experimentUnitFingerprint())
                    || !outcome.admissionPolicyVersion().equals(receipt.admissionPolicyVersion())
                    || !outcome.experimentUnitFingerprint().matches("[0-9a-f]{64}")
                    || !outcome.evidence().contains(new EvidenceReference(EXPERIMENT_UNIT_EVIDENCE_TYPE,outcome.experimentUnitFingerprint()))
                    || outcome.verifiedAt().isBefore(receipt.receivedAt()) || !verifierId.equals(outcome.verifierId()))
                throw new SecurityException("persisted coalition outcome no longer matches its exact selected coalition receipt");
            if(!verifier.verify(scopeId,outcome.receiptId(),outcome.coalitionId(),outcome.signal(),outcome.evidence(),outcome.verifiedAt()))
                throw new SecurityException("persisted coalition outcome proof failed revalidation");
            return new VerifiedWorkspaceCoalitionOutcomeEvidence(outcome,outcome.experimentUnitFingerprint());
        }).toList();
    }

    public static String receiptId(String workspaceId,String consumerId) { return required(workspaceId,"workspaceId")+"#"+required(consumerId,"consumerId"); }

    public static String candidateFingerprint(WorkspaceCandidate c) {
        String canonical=c.id()+"|"+c.scopeId().value()+"|"+c.kind()+"|"+Double.toHexString(c.utilityPoints())+"|"
                +Double.toHexString(c.reliability())+"|"+Double.toHexString(c.risk())+"|"+c.contextUnits()+"|"
                +c.coverageTags().stream().sorted().toList()+"|"+c.conflictsWith().stream().sorted().toList()+"|"+c.mandatory()+"|"
                +c.evidence().stream().map(e->e.evidenceType()+":"+e.evidenceId()).sorted().toList();
        return sha256(canonical);
    }
    private static String workspaceFingerprint(WorkspaceSnapshot s) {
        String canonical=s.id()+"|"+s.cycleId()+"|"+s.scopeId().value()+"|"+s.capacityUnits()+"|"+s.usedUnits()+"|"
                +s.uncertaintyDirective()+"|"+s.entries().stream().map(e->e.candidate().id()+":"+candidateFingerprint(e.candidate())).toList();
        return sha256(canonical);
    }
    private static String sha256(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 unavailable",impossible);}
    }
    private static String required(String value,String name){String s=Objects.requireNonNull(value,name).trim();if(s.isEmpty())throw new IllegalArgumentException(name+" cannot be blank");return s;}
}
