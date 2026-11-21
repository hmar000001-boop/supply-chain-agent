package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.mechanisms.b1.B1MemoryAttribution;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.mechanisms.b6.UncertaintyGovernanceDirective;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.List;

/** Durable audit projection of a consumer receipt; active WorkspaceSnapshot state remains ephemeral. */
public record WorkspaceReceiptEvidence(String receiptId, MemoryScopeId scopeId, String cycleId,
        String workspaceId, String consumerId, ConsumerReceiptStatus status,
        Map<String,String> consumedCandidateFingerprints, String workspaceFingerprint,
        String experimentUnitFingerprint, String admissionPolicyVersion, Instant receivedAt,
        Map<String, List<B1MemoryAttribution>> memoryAttributionsByCandidate,
        Map<String, WorkspaceCoalitionReceipt> selectedCoalitions,
        UncertaintyGovernanceDirective uncertaintyDirective) {
    public WorkspaceReceiptEvidence {
        receiptId=required(receiptId,"receiptId"); scopeId=Objects.requireNonNull(scopeId,"scopeId");
        cycleId=required(cycleId,"cycleId"); workspaceId=required(workspaceId,"workspaceId");
        consumerId=required(consumerId,"consumerId"); status=Objects.requireNonNull(status,"status");
        var copy=new TreeMap<String,String>();
        Objects.requireNonNull(consumedCandidateFingerprints,"consumedCandidateFingerprints").forEach((id,fp)->{
            String candidate=required(id,"candidateId"), fingerprint=required(fp,"candidateFingerprint");
            if(!fingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("candidate fingerprint must be SHA-256");
            copy.put(candidate,fingerprint);
        });
        consumedCandidateFingerprints=Map.copyOf(copy);
        if(status!=ConsumerReceiptStatus.ACCEPTED && !consumedCandidateFingerprints.isEmpty())
            throw new IllegalArgumentException("non-accepted receipt cannot claim consumed candidates");
        workspaceFingerprint=required(workspaceFingerprint,"workspaceFingerprint");
        if(!workspaceFingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("workspaceFingerprint must be SHA-256");
        experimentUnitFingerprint=experimentUnitFingerprint==null?"":experimentUnitFingerprint.trim();
        if(!experimentUnitFingerprint.isEmpty()&&!experimentUnitFingerprint.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("experimentUnitFingerprint must be empty or SHA-256");
        admissionPolicyVersion=required(admissionPolicyVersion,"admissionPolicyVersion");
        receivedAt=Objects.requireNonNull(receivedAt,"receivedAt");
        uncertaintyDirective = Objects.requireNonNull(uncertaintyDirective, "uncertaintyDirective");
        if (!uncertaintyDirective.assessmentReference().memoryScopeId().equals(scopeId.value()))
            throw new SecurityException("B3 receipt B6 directive scope mismatch");
        final var stableScope = scopeId;
        final var stableWorkspaceId = workspaceId;
        final var stableReceiptStatus = status;
        final var stableConsumedFingerprints = consumedCandidateFingerprints;
        var attributionCopy=new TreeMap<String,List<B1MemoryAttribution>>();
        (memoryAttributionsByCandidate == null ? Map.<String,List<B1MemoryAttribution>>of() : memoryAttributionsByCandidate).forEach((candidate,values)->{
            String id=required(candidate,"attribution candidate ID");
            if(!stableConsumedFingerprints.containsKey(id)) throw new SecurityException("B1 attribution must belong to a consumed candidate");
            List<B1MemoryAttribution> stable=List.copyOf(values);
            if(stable.stream().anyMatch(a->!stableScope.equals(a.reference().scopeId()))) throw new SecurityException("B1 receipt attribution scope mismatch");
            String exactCandidateFingerprint = stableConsumedFingerprints.get(id);
            if (stable.stream().anyMatch(a -> !exactCandidateFingerprint.equals(a.sourceCandidateFingerprint())))
                throw new SecurityException("B1 attribution is not bound to the exact consumed workspace candidate fingerprint");
            attributionCopy.put(id,stable);
        });
        memoryAttributionsByCandidate=Map.copyOf(attributionCopy);
        var coalitionCopy=new TreeMap<String,WorkspaceCoalitionReceipt>();
        (selectedCoalitions == null ? Map.<String,WorkspaceCoalitionReceipt>of() : selectedCoalitions).forEach((id,coalition)->{
            if(!required(id,"coalition ID").equals(coalition.coalitionId())) throw new IllegalArgumentException("coalition receipt map key mismatch");
            if(!stableScope.equals(coalition.scopeId()) || !stableWorkspaceId.equals(coalition.workspaceId())) throw new SecurityException("coalition receipt scope/workspace mismatch");
            if(stableReceiptStatus!=ConsumerReceiptStatus.ACCEPTED || !stableConsumedFingerprints.keySet().containsAll(coalition.candidateIds()))
                throw new SecurityException("coalition receipt requires an accepted receipt consuming every member");
            coalitionCopy.put(id,coalition);
        });
        selectedCoalitions=Map.copyOf(coalitionCopy);
        if(status!=ConsumerReceiptStatus.ACCEPTED && (!memoryAttributionsByCandidate.isEmpty()||!selectedCoalitions.isEmpty()))
            throw new IllegalArgumentException("non-accepted receipt cannot carry attribution/coalition evidence");
    }

    /** Compatibility constructor for audit receipts that are not eligible for adaptive training. */
    public WorkspaceReceiptEvidence(String receiptId, MemoryScopeId scopeId, String cycleId, String workspaceId,
            String consumerId, ConsumerReceiptStatus status, Map<String,String> consumedCandidateFingerprints,
            String workspaceFingerprint, String experimentUnitFingerprint, String admissionPolicyVersion, Instant receivedAt) {
        this(receiptId,scopeId,cycleId,workspaceId,consumerId,status,consumedCandidateFingerprints,workspaceFingerprint,
                experimentUnitFingerprint,admissionPolicyVersion,receivedAt,Map.of(),Map.of(),neutralDirective(scopeId));
    }
    /** Compatibility constructor retaining B1 attribution and coalition receipts without B6 trace data. */
    public WorkspaceReceiptEvidence(String receiptId, MemoryScopeId scopeId, String cycleId, String workspaceId,
            String consumerId, ConsumerReceiptStatus status, Map<String,String> consumedCandidateFingerprints,
            String workspaceFingerprint, String experimentUnitFingerprint, String admissionPolicyVersion, Instant receivedAt,
            Map<String, List<B1MemoryAttribution>> memoryAttributionsByCandidate,
            Map<String, WorkspaceCoalitionReceipt> selectedCoalitions) {
        this(receiptId, scopeId, cycleId, workspaceId, consumerId, status, consumedCandidateFingerprints,
                workspaceFingerprint, experimentUnitFingerprint, admissionPolicyVersion, receivedAt,
                memoryAttributionsByCandidate, selectedCoalitions, neutralDirective(scopeId));
    }

    public WorkspaceReceiptEvidence(String receiptId, MemoryScopeId scopeId, String cycleId, String workspaceId,
            String consumerId, ConsumerReceiptStatus status, Map<String,String> consumedCandidateFingerprints,
            String workspaceFingerprint, String admissionPolicyVersion, Instant receivedAt) {
        this(receiptId,scopeId,cycleId,workspaceId,consumerId,status,consumedCandidateFingerprints,
                workspaceFingerprint,"",admissionPolicyVersion,receivedAt,Map.of(),Map.of());
    }
    private static UncertaintyGovernanceDirective neutralDirective(MemoryScopeId scope) {
        var reference = new dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment.Reference(
                "receipt-standalone-neutral", 1, "receipt-standalone", scope.value());
        return new UncertaintyGovernanceDirective(reference,
                dev.supplychain.agent.mechanisms.b6.UncertaintyAssessment.CalibrationStatus.NOT_CALIBRATED,
                "", "", 0, 0, 0, 0, 1, 1, false, false);
    }
    private static String required(String value,String name){String s=Objects.requireNonNull(value,name).trim();if(s.isEmpty())throw new IllegalArgumentException(name+" cannot be blank");return s;}
}
