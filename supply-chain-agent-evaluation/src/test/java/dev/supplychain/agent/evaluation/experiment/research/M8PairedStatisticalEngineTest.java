package dev.supplychain.agent.evaluation.experiment.research;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M8PairedStatisticalEngineTest {
    private final M8PairedStatisticalEngine engine = new M8PairedStatisticalEngine();

    @Test
    void continuousBootstrapAndSignFlipAreDeterministicAndUseExactlyTenThousandResamples() {
        var values = List.of(
                new M8PairedStatisticalEngine.PairedValue("u1", "family-a", "p1", 0.10),
                new M8PairedStatisticalEngine.PairedValue("u2", "family-a", "p1", 0.08),
                new M8PairedStatisticalEngine.PairedValue("u3", "family-a", "p2", 0.03),
                new M8PairedStatisticalEngine.PairedValue("u4", "family-b", "p1", 0.11),
                new M8PairedStatisticalEngine.PairedValue("u5", "family-b", "p2", -0.02),
                new M8PairedStatisticalEngine.PairedValue("u6", "family-b", "p2", 0.06));
        var first = engine.analyze(values, "protocol|H1|test", false);
        var second = engine.analyze(values, "protocol|H1|test", false);
        assertEquals(first, second);
        assertEquals(10_000, first.bootstrapReplicates());
        assertEquals(4, first.stratumCount());
        assertEquals("M8_PAIRED_HIERARCHICAL_CLUSTER_STRATIFIED_BOOTSTRAP_V2", first.implementationId());
        assertEquals("PAIRED_SIGN_FLIP_MONTE_CARLO_10000", first.inferenceMethod());
        assertTrue(first.confidenceIntervalLower() <= first.meanImprovement());
        assertTrue(first.confidenceIntervalUpper() >= first.meanImprovement());
        assertTrue(first.twoSidedPValue() > 0.0 && first.twoSidedPValue() <= 1.0);
    }

    @Test
    void binaryEndpointUsesExactMcNemarSignTestAndIgnoresConcordantPairs() {
        var values = List.of(
                new M8PairedStatisticalEngine.PairedValue("u1", "p1", 1.0),
                new M8PairedStatisticalEngine.PairedValue("u2", "p1", 1.0),
                new M8PairedStatisticalEngine.PairedValue("u3", "p1", 0.0),
                new M8PairedStatisticalEngine.PairedValue("u4", "p2", -1.0),
                new M8PairedStatisticalEngine.PairedValue("u5", "p2", 0.0));
        var result = engine.analyze(values, "protocol|H2|binary", true);
        assertEquals("EXACT_MCNEMAR_SIGN_TEST", result.inferenceMethod());
        assertEquals(1.0, result.twoSidedPValue(), 1e-12);
        assertEquals(10_000, result.bootstrapReplicates());
    }

    @Test
    void rejectsDuplicateEpisodeIdsAndNonBinaryDeltasForBinaryInference() {
        assertThrows(SecurityException.class, () -> engine.analyze(List.of(
                new M8PairedStatisticalEngine.PairedValue("same", "p", 0.1),
                new M8PairedStatisticalEngine.PairedValue("same", "p", 0.2)), "duplicate", false));
        assertThrows(IllegalArgumentException.class, () -> engine.analyze(List.of(
                new M8PairedStatisticalEngine.PairedValue("u1", "p", 0.5),
                new M8PairedStatisticalEngine.PairedValue("u2", "p", 0.0)), "not-binary", true));
    }
}
