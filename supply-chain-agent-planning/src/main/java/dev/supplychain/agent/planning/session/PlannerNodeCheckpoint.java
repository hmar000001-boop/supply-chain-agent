package dev.supplychain.agent.planning.session;

import java.util.List;
import java.util.Objects;

/** Replayable search-tree node metadata; its domain state is reconstructed from the saved action path. */
public record PlannerNodeCheckpoint(String nodeId, String parentNodeId, List<String> actionIds,
                                    long visits, double totalReward, List<String> expandedActionIds,
                                    boolean fullyExpanded) {
    public PlannerNodeCheckpoint {
        nodeId = require(nodeId, "nodeId");
        parentNodeId = parentNodeId == null || parentNodeId.isBlank() ? null : parentNodeId.trim();
        actionIds = cleanIds(actionIds, "actionIds");
        if (visits < 0 || !Double.isFinite(totalReward) || totalReward < 0) {
            throw new IllegalArgumentException("invalid node statistics");
        }
        expandedActionIds = cleanIds(expandedActionIds, "expandedActionIds");
    }
    private static List<String> cleanIds(List<String> values, String name) {
        List<String> cleaned = List.copyOf(Objects.requireNonNull(values, name));
        if (cleaned.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(name + " cannot contain blank IDs");
        }
        if (cleaned.stream().distinct().count() != cleaned.size()) {
            throw new IllegalArgumentException(name + " cannot contain duplicate IDs");
        }
        return cleaned;
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
