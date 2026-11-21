package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.memory.procedural.ProcedureVersion;
import java.util.Objects;

/** Retrieved, active recovery knowledge; it is a planning hint and never an executable command. */
public record ReusableRecoveryProcedure(ProcedureVersion procedure, RecoveryProcedureArtifact artifact) {
    public ReusableRecoveryProcedure {
        procedure = Objects.requireNonNull(procedure, "procedure");
        artifact = Objects.requireNonNull(artifact, "artifact");
        if (!procedure.scopeId().equals(artifact.scopeId())) throw new SecurityException("procedure/artifact scope mismatch");
        if (!procedure.provenance().sourceId().equals(artifact.sourceDraftId())
                || !Objects.equals(procedure.provenance().sourceVersion(), artifact.sourceFingerprint()))
            throw new SecurityException("procedure provenance does not match recovery artifact");
    }
}
