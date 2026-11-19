package dev.supplychain.agent.evaluation.runner;

import java.util.List;
import java.util.Objects;

public record EvaluationBatch(EvaluationBatchManifest manifest, List<EvaluationRunRecord> runs) {
    public EvaluationBatch {
        manifest = Objects.requireNonNull(manifest, "manifest");
        runs = List.copyOf(Objects.requireNonNull(runs, "runs"));
        if (runs.size() != manifest.runCount()) throw new IllegalArgumentException("manifest run count does not match result rows");
    }
    public String toJsonLines() {
        StringBuilder out = new StringBuilder(manifest.toJsonLine()).append('\n');
        runs.forEach(run -> out.append(run.toJsonLine()).append('\n'));
        return out.toString();
    }
}
