package dev.supplychain.agent.mechanisms.b3;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;

/** Trusted application adapter that validates the claimed outcome against authoritative downstream records. */
@FunctionalInterface
public interface DownstreamOutcomeEvidenceVerifier {
    boolean verify(MemoryScopeId scopeId, String receiptId, String candidateId,
                   DownstreamOutcomeSignal signal, List<EvidenceReference> evidence, Instant at);
}
