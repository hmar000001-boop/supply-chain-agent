package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Untrusted report about the downstream result of one exact, accepted coalition receipt. */
public record WorkspaceCoalitionOutcomeReport(String idempotencyKey, String receiptId,
        MemoryScopeId scopeId, String coalitionId, DownstreamOutcomeSignal signal,
        List<EvidenceReference> evidence, Instant verifiedAt) {
    public WorkspaceCoalitionOutcomeReport {
        idempotencyKey=required(idempotencyKey,"idempotencyKey"); receiptId=required(receiptId,"receiptId");
        scopeId=Objects.requireNonNull(scopeId,"scopeId"); coalitionId=required(coalitionId,"coalitionId");
        signal=Objects.requireNonNull(signal,"signal"); evidence=List.copyOf(Objects.requireNonNull(evidence,"evidence"));
        if(evidence.isEmpty())throw new IllegalArgumentException("coalition outcome requires source evidence");
        verifiedAt=Objects.requireNonNull(verifiedAt,"verifiedAt");
    }
    private static String required(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
