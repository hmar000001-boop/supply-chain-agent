package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Input to the trusted verifier boundary; never treat a model-authored report as verified. */
public record DownstreamOutcomeReport(String idempotencyKey, String receiptId, MemoryScopeId scopeId,
        String candidateId, DownstreamOutcomeSignal signal,
        List<EvidenceReference> evidence, Instant verifiedAt) {
    public DownstreamOutcomeReport {
        idempotencyKey=required(idempotencyKey,"idempotencyKey"); receiptId=required(receiptId,"receiptId");
        scopeId=Objects.requireNonNull(scopeId,"scopeId"); candidateId=required(candidateId,"candidateId");
        signal=Objects.requireNonNull(signal,"signal");
        evidence=List.copyOf(Objects.requireNonNull(evidence,"evidence"));
        if(evidence.isEmpty()) throw new IllegalArgumentException("source evidence is required");
        verifiedAt=Objects.requireNonNull(verifiedAt,"verifiedAt");
    }
    private static String required(String value,String name){String s=Objects.requireNonNull(value,name).trim();if(s.isEmpty())throw new IllegalArgumentException(name+" cannot be blank");return s;}
}
