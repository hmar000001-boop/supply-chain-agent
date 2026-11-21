package dev.supplychain.agent.mechanisms.b4;

import java.util.Objects;
import java.util.Set;

/**
 * Explicit authority contract for intention transitions. The outer host remains responsible for
 * authenticating the actor; this policy limits which authenticated identities may request which
 * B4 transitions. Model proposals alone are never actors.
 */
public record IntentionAuthorityPolicy(String objectiveOwner, String decisionGovernor,
        Set<String> transitionActors, Set<String> terminationActors,
        boolean terminationRequiresIndependentHostAuthorization) {
    public IntentionAuthorityPolicy {
        objectiveOwner = required(objectiveOwner, "objectiveOwner");
        decisionGovernor = required(decisionGovernor, "decisionGovernor");
        transitionActors = normalized(transitionActors);
        terminationActors = normalized(terminationActors);
        if (!transitionActors.contains(objectiveOwner) || !transitionActors.contains(decisionGovernor)) {
            throw new IllegalArgumentException("owner and B4 governor must be recognized transition actors");
        }
        if (terminationActors.isEmpty()) throw new IllegalArgumentException("at least one termination actor is required");
        if (!terminationRequiresIndependentHostAuthorization) {
            throw new IllegalArgumentException("termination must retain the independent host-authorization boundary");
        }
    }

    public static IntentionAuthorityPolicy standard(String owner) {
        String normalizedOwner = required(owner, "owner");
        return new IntentionAuthorityPolicy(normalizedOwner, "b4-governor",
                Set.of(normalizedOwner, "b4-governor"), Set.of(normalizedOwner, "operator"), true);
    }

    public void requireTransitionActor(String actor) {
        String value = required(actor, "actor");
        if (!transitionActors.contains(value)) throw new SecurityException("actor is not authorized to change the B4 intention");
    }

    public void requireTerminationActor(String actor) {
        String value = required(actor, "actor");
        if (!terminationActors.contains(value)) throw new SecurityException("actor is not authorized by the intention termination policy");
    }

    private static Set<String> normalized(Set<String> values) {
        Objects.requireNonNull(values, "values");
        return values.stream().map(v -> required(v, "actor")).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
