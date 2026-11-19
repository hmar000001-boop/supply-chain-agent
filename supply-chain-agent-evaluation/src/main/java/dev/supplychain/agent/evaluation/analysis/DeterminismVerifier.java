package dev.supplychain.agent.evaluation.analysis;

import dev.supplychain.agent.evaluation.runner.EvaluationBatch;
import dev.supplychain.agent.evaluation.runner.EvaluationRunRecord;
import dev.supplychain.agent.evaluation.runner.M8EvaluationRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Replays every scenario/procedure pair and compares logical outputs, excluding wall-clock duration. */
public final class DeterminismVerifier {
    public DeterminismCheck verify(EvaluationBatch baseline, M8EvaluationRunner runner) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(runner, "runner");
        var mismatches = new ArrayList<String>();
        int checked = 0;
        for (EvaluationRunRecord expected : baseline.runs()) {
            EvaluationRunRecord replay = runner.runOne(expected.scenarioId(), expected.plannerId());
            checked++;
            if (!expected.logicalFingerprint().equals(replay.logicalFingerprint())) {
                mismatches.add(expected.scenarioId() + "/" + expected.plannerId());
            }
        }
        return new DeterminismCheck(baseline.manifest().baselineId(), baseline.manifest().corpusVersion(),
                baseline.manifest().corpusFingerprint(), checked, checked - mismatches.size(), mismatches);
    }

    public record DeterminismCheck(String baselineId, String corpusVersion, String corpusFingerprint,
                                   int replayedRunCount, int matchingRunCount, List<String> mismatchedRunIds) {
        public DeterminismCheck {
            mismatchedRunIds = List.copyOf(Objects.requireNonNull(mismatchedRunIds, "mismatchedRunIds"));
            if (replayedRunCount < 0 || matchingRunCount < 0 || matchingRunCount > replayedRunCount
                    || mismatchedRunIds.size() != replayedRunCount - matchingRunCount) {
                throw new IllegalArgumentException("invalid determinism-check counts");
            }
        }
        public boolean deterministic() { return mismatchedRunIds.isEmpty(); }
        public String toJsonLine() {
            return "{\"recordType\":\"logicalDeterminismCheck\",\"baselineId\":" + EvaluationRunRecord.quote(baselineId)
                    + ",\"corpusVersion\":" + EvaluationRunRecord.quote(corpusVersion)
                    + ",\"corpusFingerprint\":" + EvaluationRunRecord.quote(corpusFingerprint)
                    + ",\"replayedRunCount\":" + replayedRunCount + ",\"matchingRunCount\":" + matchingRunCount
                    + ",\"deterministic\":" + deterministic() + ",\"mismatchedRunIds\":["
                    + mismatchedRunIds.stream().map(EvaluationRunRecord::quote).collect(java.util.stream.Collectors.joining(",")) + "]}";
        }
    }
}
