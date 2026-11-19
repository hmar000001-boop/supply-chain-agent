package dev.supplychain.agent.evaluation.fixture;

import dev.supplychain.agent.evaluation.catalog.ScenarioDefinition;
import dev.supplychain.agent.planning.api.PlanningRequest;

import java.util.Objects;

/** One immutable, independently identified planning fixture. It is a simulator input, never an execution grant. */
public record ScenarioFixture(
        ScenarioDefinition definition,
        PlanningRequest planningRequest,
        String initialWorldFingerprint,
        String fixtureFingerprint) {
    public ScenarioFixture {
        definition = Objects.requireNonNull(definition, "definition");
        planningRequest = Objects.requireNonNull(planningRequest, "planningRequest");
        initialWorldFingerprint = required(initialWorldFingerprint, "initialWorldFingerprint");
        fixtureFingerprint = required(fixtureFingerprint, "fixtureFingerprint");
        if (!planningRequest.executionId().equals("m8-eval:" + definition.scenarioId())) {
            throw new IllegalArgumentException("fixture execution identity must be derived from its scenario ID");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
