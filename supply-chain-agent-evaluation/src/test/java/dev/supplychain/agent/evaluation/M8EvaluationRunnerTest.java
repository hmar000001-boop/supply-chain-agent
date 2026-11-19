package dev.supplychain.agent.evaluation;

import dev.supplychain.agent.evaluation.catalog.ScenarioCatalog;
import dev.supplychain.agent.evaluation.runner.M8EvaluationRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M8EvaluationRunnerTest {
    @Test
    void emitsMatchedPlannerOnlyRowsForTheCompleteCorpus() {
        var runner = new M8EvaluationRunner(ScenarioCatalog.loadDefault());
        var batch = runner.runAll();

        assertEquals(25, batch.manifest().scenarioCount());
        assertEquals(5, batch.manifest().plannerIds().size());
        assertEquals(125, batch.manifest().runCount());
        assertEquals(125, batch.runs().size());
        assertTrue(batch.runs().stream().allMatch(row -> row.plannerOnly() && !row.externalDispatchAttempted()));
        assertTrue(batch.runs().stream().allMatch(row -> row.fixtureFingerprint().length() == 64));
        assertEquals(64, batch.manifest().plannerConfigurationFingerprint().length());
        assertTrue(batch.toJsonLines().contains("\"plannerConfigurationFingerprint\""));
        assertTrue(batch.toJsonLines().contains("\"resourceUsage\":"));
        assertTrue(batch.toJsonLines().contains("\"prunedBranches\":"));
        assertTrue(batch.toJsonLines().contains("\"backtracks\":"));
        assertFalse(batch.toJsonLines().isBlank());
    }
}
