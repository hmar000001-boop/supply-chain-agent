package dev.supplychain.agent.runtime.cognitive.lifecycle;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe reference store only; not a crash-durable production repository. */
public final class InMemoryAuthorizedActionPlanStore implements AuthorizedActionPlanStore {
    private final ConcurrentHashMap<String, AuthorizedActionPlan> plans = new ConcurrentHashMap<>();

    @Override public void save(AuthorizedActionPlan plan) {
        Objects.requireNonNull(plan, "plan");
        String key = key(plan.executionId(), plan.candidateSetId(), plan.candidateId());
        AuthorizedActionPlan existing = plans.putIfAbsent(key, plan);
        if (existing != null && !existing.fingerprint().equals(plan.fingerprint())) {
            throw new SecurityException("reconstructed action plan does not match the previously authorized plan");
        }
    }

    @Override public Optional<AuthorizedActionPlan> find(String executionId, String candidateSetId, String candidateId) {
        return Optional.ofNullable(plans.get(key(executionId, candidateSetId, candidateId)));
    }

    private static String key(String execution, String set, String candidate) {
        return required(execution) + "|" + required(set) + "|" + required(candidate);
    }
    private static String required(String v) {
        String s = Objects.requireNonNull(v, "key").trim();
        if (s.isEmpty()) throw new IllegalArgumentException("key cannot be blank");
        return s;
    }
}
