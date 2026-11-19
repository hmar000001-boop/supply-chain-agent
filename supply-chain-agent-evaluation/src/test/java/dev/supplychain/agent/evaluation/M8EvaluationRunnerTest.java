package dev.supplychain.agent.evaluation;

import dev.supplychain.agent.evaluation.analysis.MatchedComparisonAnalyzer;
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
        assertTrue(batch.runs().stream().allMatch(row -> row.candidateOutcomes().size() == row.candidateCount()));
        assertTrue(batch.runs().stream().allMatch(row -> row.executionOrderIndex() >= 0 && row.executionOrderIndex() < 5));
        assertTrue(batch.runs().stream().allMatch(row -> row.scenarioOrderIndex() >= 0 && row.scenarioOrderIndex() < 25));
        assertEquals(64, batch.manifest().plannerConfigurationFingerprint().length());
        assertTrue(batch.toJsonLines().contains("\"plannerConfigurationFingerprint\""));
        assertTrue(batch.toJsonLines().contains("\"resourceUsage\":"));
        assertTrue(batch.toJsonLines().contains("\"candidateOutcomes\":"));
        assertTrue(batch.toJsonLines().contains("\"trace\":"));
        assertTrue(batch.toJsonLines().contains("\"scenarioOrderIndex\":"));
        assertTrue(batch.toJsonLines().contains("\"executionOrderIndex\":"));
        assertTrue(batch.toJsonLines().contains("\"prunedBranches\":"));
        assertTrue(batch.toJsonLines().contains("\"backtracks\":"));
        var analysis = new MatchedComparisonAnalyzer().analyze(batch);
        assertEquals(10, analysis.pairwiseComparisons().size());
        assertEquals(5, analysis.aggregates().stream().filter(row -> row.dimension().equals("OVERALL")).count());
        assertTrue(analysis.pairwiseComparisons().stream().allMatch(pair -> pair.pairedScenarioCount() == 25));
        assertTrue(analysis.pairwiseComparisons().stream().allMatch(pair -> pair.leftWins() + pair.ties() + pair.rightWins() == 25));
        assertTrue(analysis.pairwiseComparisons().stream().allMatch(pair -> pair.scoreComparedScenarioCount() <= 25));
        assertFalse(analysis.toJsonLines().isBlank());
        assertFalse(batch.toJsonLines().isBlank());
    }
}
