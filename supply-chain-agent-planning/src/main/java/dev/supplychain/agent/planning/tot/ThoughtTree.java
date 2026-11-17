package dev.supplychain.agent.planning.tot;

import java.util.Map;
import java.util.Objects;

public record ThoughtTree(String rootId, Map<String, ThoughtNode> nodes) {
    public ThoughtTree {
        rootId = Objects.requireNonNull(rootId, "rootId");
        nodes = Map.copyOf(Objects.requireNonNull(nodes, "nodes"));
        if (!nodes.containsKey(rootId)) throw new IllegalArgumentException("thought tree must contain its root");
        for (var entry : nodes.entrySet()) {
            ThoughtNode node = entry.getValue();
            if (!entry.getKey().equals(node.id())) throw new IllegalArgumentException("thought tree key mismatch");
            if (node.parentId() != null && !nodes.containsKey(node.parentId())) throw new IllegalArgumentException("thought node parent is missing");
            for (String child : node.children()) {
                if (!nodes.containsKey(child)) throw new IllegalArgumentException("thought node child is missing");
            }
        }
    }
}
