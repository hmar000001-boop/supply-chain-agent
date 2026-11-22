package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.research.M8CandidateCodeFingerprint;
import dev.supplychain.agent.evaluation.experiment.research.M8Ed25519AttestationTrust;
import dev.supplychain.agent.mechanisms.b2.ReusableRecoveryProcedure;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidence;
import dev.supplychain.agent.mechanisms.b3.WorkspaceCoalitionOutcomeEvidence;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.mechanisms.b1.B1MemoryAttribution;

import java.time.Instant;
import java.util.Objects;

/**
 * Creates non-permissive B1/B2/B3/B6 artifact verifiers backed by separately issued Ed25519
 * attestations. It never signs evidence; the configured issuers must be provisioned externally.
 */
public final class Ed25519MechanismTrainingArtifactVerifierServices {
    private final M8Ed25519AttestationTrust trust;
    private final String b1Issuer;
    private final String b2Issuer;
    private final String b3Issuer;
    private final String b6Issuer;

    public Ed25519MechanismTrainingArtifactVerifierServices(M8Ed25519AttestationTrust trust,
            String b1Issuer, String b2Issuer, String b3Issuer, String b6Issuer) {
        this.trust = Objects.requireNonNull(trust, "trust");
        this.b1Issuer = required(b1Issuer); this.b2Issuer = required(b2Issuer);
        this.b3Issuer = required(b3Issuer); this.b6Issuer = required(b6Issuer);
        if (java.util.Set.of(this.b1Issuer, this.b2Issuer, this.b3Issuer, this.b6Issuer).size() != 4)
            throw new IllegalArgumentException("B1/B2/B3/B6 evidence issuers must be distinct");
    }

    public MechanismTrainingArtifactVerificationServices create() {
        return new MechanismTrainingArtifactVerificationServices(
                outcome -> verifyQuietly("M8_B1_DOWNSTREAM_OUTCOME", b1Subject(outcome), b1Issuer),
                (procedure, at) -> at != null && !at.isBefore(procedure.procedure().createdAt())
                        && verifyQuietly("M8_B2_APPROVED_PROCEDURE", b2Subject(procedure), b2Issuer),
                outcome -> verifyQuietly("M8_B3_COALITION_OUTCOME", b3Subject(outcome), b3Issuer),
                label -> verifyQuietly("M8_B6_SIMULATOR_LABEL", b6Subject(label), b6Issuer));
    }

    private boolean verifyQuietly(String type, String subject, String issuer) {
        try { trust.verify(type, subject, issuer); return true; }
        catch (RuntimeException invalid) { return false; }
    }

    private static String b1Subject(DownstreamOutcomeEvidence value) {
        Objects.requireNonNull(value, "outcome");
        String attributions = value.memoryAttributions().stream().map(Ed25519MechanismTrainingArtifactVerifierServices::attribution)
                .sorted().toList().toString();
        String refs = value.evidence().stream().map(Ed25519MechanismTrainingArtifactVerifierServices::reference)
                .sorted().toList().toString();
        return M8CandidateCodeFingerprint.sha256(value.idempotencyKey() + "|" + value.receiptId() + "|"
                + value.scopeId().value() + "|" + value.cycleId() + "|" + value.workspaceId() + "|"
                + value.candidateId() + "|" + value.candidateFingerprint() + "|" + value.consumerId() + "|"
                + value.signal() + "|" + value.verifierId() + "|" + refs + "|"
                + value.experimentUnitFingerprint() + "|" + value.admissionPolicyVersion() + "|"
                + value.verifiedAt() + "|" + attributions);
    }

    private static String b2Subject(ReusableRecoveryProcedure value) {
        Objects.requireNonNull(value, "procedure");
        var procedure = value.procedure();
        var artifact = value.artifact();
        String canonical = procedure.id() + "|" + procedure.scopeId().value() + "|" + procedure.procedureKey()
                + "|" + procedure.versionNumber() + "|" + procedure.kind() + "|" + procedure.description()
                + "|" + procedure.artifact() + "|" + procedure.provenance() + "|" + procedure.status()
                + "|" + procedure.lifecycleRevision() + "|" + procedure.createdAt() + "|" + procedure.createdBy()
                + "|" + procedure.validatedAt() + "|" + procedure.validatedBy() + "|" + procedure.activatedAt()
                + "|" + procedure.activatedBy() + "|" + procedure.retiredAt() + "|" + procedure.rejectedAt()
                + "|" + procedure.rejectedBy() + "|" + procedure.predecessor() + "|" + artifact.formatVersion()
                + "|" + artifact.sourceDraftId() + "|" + artifact.scopeId().value() + "|" + artifact.conditionKey()
                + "|" + artifact.objectiveFingerprint() + "|" + artifact.actions() + "|" + artifact.preconditionKeys()
                + "|" + artifact.expectedPostconditionKeys() + "|" + artifact.failureConditionKeys() + "|"
                + artifact.evidence().stream().map(Ed25519MechanismTrainingArtifactVerifierServices::reference).sorted().toList()
                + "|" + artifact.sourceFingerprint() + "|" + artifact.approvedBy() + "|"
                + artifact.approvalReference() + "|" + artifact.approvedAt();
        return M8CandidateCodeFingerprint.sha256(canonical);
    }

    private static String b3Subject(WorkspaceCoalitionOutcomeEvidence value) {
        Objects.requireNonNull(value, "outcome");
        return M8CandidateCodeFingerprint.sha256(value.idempotencyKey() + "|" + value.receiptId() + "|"
                + value.scopeId().value() + "|" + value.workspaceId() + "|" + value.cycleId() + "|"
                + value.consumerId() + "|" + value.coalitionId() + "|" + value.proposalFingerprint() + "|"
                + value.preferenceKey() + "|" + value.candidateIds().stream().sorted().toList() + "|"
                + value.signal() + "|" + value.verifierId() + "|"
                + value.evidence().stream().map(Ed25519MechanismTrainingArtifactVerifierServices::reference).sorted().toList()
                + "|" + value.experimentUnitFingerprint() + "|" + value.admissionPolicyVersion() + "|" + value.verifiedAt());
    }

    private static String b6Subject(B6SimulatorLabelEvidence value) {
        Objects.requireNonNull(value, "label");
        var observation = value.observation();
        return M8CandidateCodeFingerprint.sha256(value.split() + "|" + value.experimentUnitFingerprint() + "|"
                + value.simulatorRunFingerprint() + "|" + value.simulatorStateFingerprint() + "|"
                + observation.sampleId() + "|" + observation.scopeId().value() + "|" + observation.targetId()
                + "|" + observation.dimension() + "|" + Double.toHexString(observation.predictedUncertainty())
                + "|" + observation.realizedError() + "|" + reference(observation.labelEvidence()) + "|" + observation.observedAt());
    }

    private static String attribution(B1MemoryAttribution value) {
        return value.reference() + "|" + value.queryCues().stream().sorted().toList() + "|" + value.sourceCandidateFingerprint();
    }
    private static String reference(EvidenceReference value) { return value.evidenceType() + ":" + value.evidenceId(); }
    private static String required(String value) {
        String result = Objects.requireNonNull(value, "issuer").trim();
        if (!result.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) throw new IllegalArgumentException("invalid issuer ID");
        return result;
    }
}
