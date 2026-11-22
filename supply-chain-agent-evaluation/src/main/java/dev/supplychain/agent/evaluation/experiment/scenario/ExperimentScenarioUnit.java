package dev.supplychain.agent.evaluation.experiment.scenario;

import dev.supplychain.agent.evaluation.fixture.ScenarioFixture;

import java.util.Objects;

/** One generated experimental unit with explicit split provenance and simulator input. */
public record ExperimentScenarioUnit(
        String protocolId,
        ExperimentSplit split,
        String scenarioFamily,
        String fixtureProfile,
        String baseScenarioId,
        String experimentUnitId,
        int profileOrdinal,
        int variantOrdinal,
        long seed,
        ExperimentScenarioParameters parameters,
        String effectiveInputFingerprint,
        ScenarioFixture fixture) {

    public ExperimentScenarioUnit {
        protocolId = required(protocolId, "protocolId");
        split = Objects.requireNonNull(split, "split");
        scenarioFamily = required(scenarioFamily, "scenarioFamily");
        fixtureProfile = required(fixtureProfile, "fixtureProfile");
        baseScenarioId = required(baseScenarioId, "baseScenarioId");
        experimentUnitId = required(experimentUnitId, "experimentUnitId");
        if (profileOrdinal < 1) throw new IllegalArgumentException("profileOrdinal must be positive");
        if (variantOrdinal < 1) throw new IllegalArgumentException("variantOrdinal must be positive");
        if (seed < 0) throw new IllegalArgumentException("seed cannot be negative");
        parameters = Objects.requireNonNull(parameters, "parameters");
        effectiveInputFingerprint = required(effectiveInputFingerprint, "effectiveInputFingerprint");
        fixture = Objects.requireNonNull(fixture, "fixture");
        if (!fixture.definition().scenarioId().equals("EXP-" + split + "-" + fixtureProfile + "-" + seed)) {
            throw new IllegalArgumentException("fixture scenario identity does not match split/profile/seed");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
