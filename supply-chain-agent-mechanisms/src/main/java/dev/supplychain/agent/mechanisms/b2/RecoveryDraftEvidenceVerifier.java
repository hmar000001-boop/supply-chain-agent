package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;

/** Trusted host adapter that dereferences every evidence reference and checks scope, version and validity. */
@FunctionalInterface
public interface RecoveryDraftEvidenceVerifier {
    boolean verify(MemoryScopeId scopeId, List<EvidenceReference> evidence, Instant at);
}
