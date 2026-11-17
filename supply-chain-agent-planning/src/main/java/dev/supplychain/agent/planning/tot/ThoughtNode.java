package dev.supplychain.agent.planning.tot;

import java.util.List;
import java.util.Objects;

/** One partial plan/world-state hypothesis in the ToT-inspired search tree. */
public record ThoughtNode(String id, String parentId, String incomingActionId, int depth,
                          double score, boolean feasible, List<String> children) {
    public ThoughtNode {
        id = require(id, "id");
        parentId = parentId == null || parentId.isBlank() ? null : parentId.trim();
        incomingActionId = incomingActionId == null || incomingActionId.isBlank() ? null : incomingActionId.trim();
        if (depth < 0 || !Double.isFinite(score) || score < 0 || score > 1) throw new IllegalArgumentException("invalid thought node");
        children = List.copyOf(Objects.requireNonNull(children, "children"));
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
