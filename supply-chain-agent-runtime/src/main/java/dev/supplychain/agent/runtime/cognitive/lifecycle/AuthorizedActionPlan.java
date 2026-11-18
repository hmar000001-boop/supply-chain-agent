package dev.supplychain.agent.runtime.cognitive.lifecycle;

import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.planning.api.PlanCandidate;
import dev.supplychain.agent.planning.api.PlanningRequest;

import java.util.List;
import java.util.Objects;

/** Exact candidate-to-M3 mapping, cached outside graph state and rebuilt/verified after restart. */
public record AuthorizedActionPlan(String executionId, String candidateSetId, String candidateId,
                                   String intentionId, long intentionVersion, String fingerprint,
                                   PlanCandidate candidate, PlanningRequest planningRequest,
                                   List<ActionDispatchCommand<?, ?>> commands,
                                   List<ActionDescriptor> descriptors, boolean stateChanging) {
    public AuthorizedActionPlan {
        executionId = required(executionId, "executionId");
        candidateSetId = required(candidateSetId, "candidateSetId");
        candidateId = required(candidateId, "candidateId");
        intentionId = required(intentionId, "intentionId");
        fingerprint = required(fingerprint, "fingerprint");
        if (intentionVersion < 0) throw new IllegalArgumentException("intentionVersion cannot be negative");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(planningRequest, "planningRequest");
        commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
        descriptors = List.copyOf(Objects.requireNonNull(descriptors, "descriptors"));
        if (commands.isEmpty() || commands.size() != candidate.actions().size()) {
            throw new IllegalArgumentException("every candidate action must have exactly one M3 command");
        }
        if (descriptors.size() != commands.size()) throw new IllegalArgumentException("descriptor count must match command count");
        if (!stateChanging) throw new SecurityException("a recovery candidate must be treated as state-changing");
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
