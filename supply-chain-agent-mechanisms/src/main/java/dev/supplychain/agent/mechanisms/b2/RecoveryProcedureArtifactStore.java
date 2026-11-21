package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.procedural.ProcedureArtifactReference;
import java.util.Optional;

/** Immutable content-addressed store for typed B2 artifacts referenced by CoALA procedural memory. */
public interface RecoveryProcedureArtifactStore {
    ProcedureArtifactReference publish(RecoveryProcedureArtifact artifact);
    Optional<RecoveryProcedureArtifact> retrieve(ProcedureArtifactReference reference, MemoryScopeId expectedScope);
}
