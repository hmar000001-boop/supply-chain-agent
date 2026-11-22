package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.mechanisms.b2.ReusableRecoveryProcedure;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Objects;

/** TRAIN-derived approved/reviewed procedures keyed by exact registered recovery condition. */
public record B2ProcedureTrainingArtifact(
        Map<String, ReusableRecoveryProcedure> proceduresByCondition,
        Map<String, Set<String>> sourceUnitsByCondition,
        String evidenceFingerprint) {
    public B2ProcedureTrainingArtifact {
        var procedures = new TreeMap<String, ReusableRecoveryProcedure>();
        Objects.requireNonNull(proceduresByCondition, "proceduresByCondition").forEach((condition, procedure) -> {
            String key = ExperimentEvidenceFingerprint.required(condition, "conditionKey");
            if (procedure == null || !key.equals(procedure.artifact().conditionKey()))
                throw new SecurityException("B2 procedure map key differs from its approved artifact condition");
            procedures.put(key, procedure);
        });
        if (procedures.isEmpty()) throw new IllegalArgumentException("B2 training artifact requires approved reusable procedures");
        proceduresByCondition = Map.copyOf(procedures);
        var units = new TreeMap<String, Set<String>>();
        Objects.requireNonNull(sourceUnitsByCondition, "sourceUnitsByCondition").forEach((condition, fingerprints) -> {
            if (!procedures.containsKey(condition)) throw new SecurityException("B2 source units refer to an unknown procedure condition");
            Set<String> copy = Objects.requireNonNull(fingerprints, "source units").stream()
                    .map(v -> ExperimentEvidenceFingerprint.requireSha(v, "source unit fingerprint"))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (copy.size() < 2) throw new IllegalArgumentException("each B2 procedure requires at least two distinct TRAIN source units");
            units.put(condition, copy);
        });
        if (!units.keySet().equals(procedures.keySet())) throw new SecurityException("B2 procedure/source-unit conditions must match exactly");
        sourceUnitsByCondition = Map.copyOf(units);
        evidenceFingerprint = ExperimentEvidenceFingerprint.requireSha(evidenceFingerprint, "evidenceFingerprint");
    }
}
