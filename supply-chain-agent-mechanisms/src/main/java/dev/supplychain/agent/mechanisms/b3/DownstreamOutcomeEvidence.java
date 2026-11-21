package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Append-only, verified candidate-use outcome linked to the exact accepted consumer receipt. */
public record DownstreamOutcomeEvidence(String idempotencyKey, String receiptId, MemoryScopeId scopeId,
        String cycleId, String workspaceId, String candidateId, String candidateFingerprint,
        String consumerId, DownstreamOutcomeSignal signal, String verifierId,
        List<EvidenceReference> evidence, String experimentUnitFingerprint, String admissionPolicyVersion, Instant verifiedAt) {
    public DownstreamOutcomeEvidence {
        idempotencyKey=required(idempotencyKey,"idempotencyKey"); receiptId=required(receiptId,"receiptId");
        scopeId=Objects.requireNonNull(scopeId,"scopeId"); cycleId=required(cycleId,"cycleId");
        workspaceId=required(workspaceId,"workspaceId"); candidateId=required(candidateId,"candidateId");
        candidateFingerprint=required(candidateFingerprint,"candidateFingerprint");
        if(!candidateFingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("candidateFingerprint must be SHA-256");
        consumerId=required(consumerId,"consumerId"); signal=Objects.requireNonNull(signal,"signal");
        verifierId=required(verifierId,"verifierId"); evidence=List.copyOf(Objects.requireNonNull(evidence,"evidence"));
        if(evidence.isEmpty()) throw new IllegalArgumentException("verified outcome requires source evidence");
        experimentUnitFingerprint=experimentUnitFingerprint==null?"":experimentUnitFingerprint.trim();
        if(!experimentUnitFingerprint.isEmpty()&&!experimentUnitFingerprint.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("experimentUnitFingerprint must be empty or SHA-256");
        admissionPolicyVersion=required(admissionPolicyVersion,"admissionPolicyVersion");
        verifiedAt=Objects.requireNonNull(verifiedAt,"verifiedAt");
    }

    /** Compatibility constructor for verified audit outcomes without experiment-training provenance. */
    public DownstreamOutcomeEvidence(String idempotencyKey, String receiptId, MemoryScopeId scopeId, String cycleId,
            String workspaceId, String candidateId, String candidateFingerprint, String consumerId,
            DownstreamOutcomeSignal signal, String verifierId, List<EvidenceReference> evidence,
            String admissionPolicyVersion, Instant verifiedAt) {
        this(idempotencyKey, receiptId, scopeId, cycleId, workspaceId, candidateId, candidateFingerprint,
                consumerId, signal, verifierId, evidence, "", admissionPolicyVersion, verifiedAt);
    }
    private static String required(String value,String name){String s=Objects.requireNonNull(value,name).trim();if(s.isEmpty())throw new IllegalArgumentException(name+" cannot be blank");return s;}
}
