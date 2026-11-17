package dev.supplychain.agent.planning.api;

import java.util.List;
import java.util.Objects;

/** Compact inspectable search trace; it deliberately excludes private model reasoning traces. */
public record PlannerTraceNode(String nodeId, String parentNodeId, String actionId, int depth,
                               double score, long visits, double meanReward,
                               String disposition, List<String> childNodeIds) {
    public PlannerTraceNode {
        nodeId = require(nodeId, "nodeId");
        parentNodeId = parentNodeId == null || parentNodeId.isBlank() ? null : parentNodeId.trim();
        actionId = actionId == null || actionId.isBlank() ? null : actionId.trim();
        if (depth < 0 || visits < 0 || !Double.isFinite(score) || score < 0 || score > 1
                || !Double.isFinite(meanReward) || meanReward < 0 || meanReward > 1) throw new IllegalArgumentException("invalid planner trace node");
        disposition = require(disposition, "disposition");
        childNodeIds = List.copyOf(Objects.requireNonNull(childNodeIds, "childNodeIds"));
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
