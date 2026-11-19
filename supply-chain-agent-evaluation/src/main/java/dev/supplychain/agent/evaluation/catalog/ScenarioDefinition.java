package dev.supplychain.agent.evaluation.catalog;

import java.util.Objects;

/** Immutable source-register row; fixture behavior is selected by a versioned, explicit profile. */
public record ScenarioDefinition(
        String scenarioId,
        String kind,
        String scenario,
        String setup,
        String expectedInvariantOrBehavior,
        String comparatorsOrControls,
        String primaryMeasurements,
        String status,
        String fixtureVersion,
        String fixtureProfile,
        ExpectedEffectBoundary expectedEffectBoundary,
        long seed,
        String budgetProfile) {

    public ScenarioDefinition {
        scenarioId = required(scenarioId, "scenarioId");
        kind = required(kind, "kind");
        scenario = required(scenario, "scenario");
        setup = required(setup, "setup");
        expectedInvariantOrBehavior = required(expectedInvariantOrBehavior, "expectedInvariantOrBehavior");
        comparatorsOrControls = required(comparatorsOrControls, "comparatorsOrControls");
        primaryMeasurements = required(primaryMeasurements, "primaryMeasurements");
        status = required(status, "status");
        fixtureVersion = required(fixtureVersion, "fixtureVersion");
        fixtureProfile = required(fixtureProfile, "fixtureProfile");
        expectedEffectBoundary = Objects.requireNonNull(expectedEffectBoundary, "expectedEffectBoundary");
        if (seed < 0) throw new IllegalArgumentException("seed cannot be negative");
        budgetProfile = required(budgetProfile, "budgetProfile");
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }

    public enum ExpectedEffectBoundary { READ_ONLY, PROPOSAL_ONLY, APPROVAL_REQUIRED }
}
