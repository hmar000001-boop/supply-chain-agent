package dev.supplychain.agent.actions.internal;

import dev.supplychain.agent.memory.common.EvidenceReference;
import java.util.List;
import java.util.Objects;

public record ReasoningRequest(String task, String context, List<EvidenceReference> evidence) {
    public ReasoningRequest {
        task = require(task, "task");
        context = Objects.requireNonNull(context, "context");
        if (context.length() > 48_000) throw new IllegalArgumentException("reasoning context exceeds 48000 characters");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if (evidence.size() > 200) throw new IllegalArgumentException("reasoning evidence exceeds 200 references");
    }
    private static String require(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
