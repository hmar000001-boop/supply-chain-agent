package dev.supplychain.agent.evaluation.runner;

import dev.supplychain.agent.evaluation.catalog.ScenarioCatalog;
import dev.supplychain.agent.evaluation.fixture.ScenarioFixture;
import dev.supplychain.agent.evaluation.fixture.ScenarioFixtureFactory;
import dev.supplychain.agent.evaluation.planner.EvaluationPlanner;
import dev.supplychain.agent.evaluation.planner.EvaluationPlannerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Runs planner-only comparisons over matched, independent, versioned fixtures. */
public final class M8EvaluationRunner {
    public static final String BASELINE_ID = "M8-M7-ACCEPTED-2026-11-19";
    public static final String CORPUS_VERSION = "M8-B1-v1";

    private final ScenarioCatalog catalog;
    private final ScenarioFixtureFactory fixtureFactory;
    private final EvaluationPlannerFactory plannerFactory;

    public M8EvaluationRunner(ScenarioCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.fixtureFactory = new ScenarioFixtureFactory(catalog);
        this.plannerFactory = new EvaluationPlannerFactory(fixtureFactory);
    }

    public EvaluationBatch runAll() {
        List<ScenarioFixture> fixtures = fixtureFactory.createAll();
        List<String> plannerIds = plannerFactory.plannerIds();
        var results = new ArrayList<EvaluationRunRecord>(fixtures.size() * plannerIds.size());
        for (int scenarioIndex = 0; scenarioIndex < fixtures.size(); scenarioIndex++) {
            ScenarioFixture fixture = fixtures.get(scenarioIndex);
            // Rotate execution order per fixture to avoid systematically giving one comparator
            // a warm JVM/cache or a more favorable position in the process timeline.
            int rotation = scenarioIndex % plannerIds.size();
            for (int orderIndex = 0; orderIndex < plannerIds.size(); orderIndex++) {
                String plannerId = plannerIds.get((orderIndex + rotation) % plannerIds.size());
                results.add(runOne(fixture, plannerId, scenarioIndex, orderIndex));
            }
        }
        String corpusFingerprint = fingerprint(fixtures.stream()
                .map(value -> value.definition().scenarioId() + "|" + value.fixtureFingerprint())
                .collect(java.util.stream.Collectors.joining("\n")));
        var manifest = new EvaluationBatchManifest(BASELINE_ID, CORPUS_VERSION, corpusFingerprint,
                ScenarioFixtureFactory.WORLD_MODEL_FINGERPRINT, ScenarioFixtureFactory.EVALUATOR_FINGERPRINT,
                plannerFactory.configurationFingerprint(), plannerIds, fixtures.size(), results.size(), fixtures.stream().map(f -> f.definition().budgetProfile()).distinct().sorted().toList(),
                "FIXED_PER_SCENARIO_SEED_FROM_M8_SCENARIO_REGISTER", ScenarioFixtureFactory.FIXTURE_AS_OF,
                "DETERMINISTIC_PLANNER_ONLY_NO_EXTERNAL_EFFECTS;MOVING_FIXTURE_CLOCK;HARD_DEADLINE_ENFORCED;ROTATING_COMPARATOR_ORDER");
        return new EvaluationBatch(manifest, results);
    }

    public EvaluationRunRecord runOne(String scenarioId, String plannerId) {
        return runOne(fixtureFactory.create(scenarioId), plannerId, 0, 0);
    }

    public List<String> plannerIds() { return plannerFactory.plannerIds(); }

    private EvaluationRunRecord runOne(ScenarioFixture fixture, String plannerId, int scenarioOrderIndex, int executionOrderIndex) {
        EvaluationPlanner planner = plannerFactory.create(fixture, plannerId);
        long started = System.nanoTime();
        var result = planner.plan(fixture.planningRequest());
        long elapsed = Math.max(0L, System.nanoTime() - started);
        return EvaluationRunRecord.from(fixture, result, elapsed, scenarioOrderIndex, executionOrderIndex);
    }

    private static String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
