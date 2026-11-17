package dev.supplychain.agent.planning.session;

import java.util.List;
import java.util.Objects;

/** A replayable path in a search frontier or a retained best-candidate set. */
public record PlannerPathCheckpoint(String nodeId, List<String> actionIds, double score) {
    public PlannerPathCheckpoint {
        nodeId = require(nodeId, "nodeId");
        actionIds = List.copyOf(Objects.requireNonNull(actionIds, "actionIds"));
        if (actionIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("actionIds cannot contain blank IDs");
        }
        if (!Double.isFinite(score) || score < 0 || score > 1) {
            throw new IllegalArgumentException("score must be in [0,1]");
        }
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
