package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.runtime.cognitive.lifecycle.AuthorizedActionPlan;
import dev.supplychain.agent.runtime.cognitive.lifecycle.AuthorizedActionPlanStore;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Immutable exact candidate/intention/action-plan binding, stored outside graph checkpoint state. */
public final class PostgresAuthorizedActionPlanStore implements AuthorizedActionPlanStore {
    private final PostgresJsonArtifactRepository<AuthorizedActionPlan> artifacts;
    private final String scopeId;
    private final java.time.Clock clock;

    public PostgresAuthorizedActionPlanStore(DataSource dataSource, String scopeId,
                                              JsonCodec<AuthorizedActionPlan> codec) {
        this(dataSource, scopeId, codec, java.time.Clock.systemUTC());
    }
    public PostgresAuthorizedActionPlanStore(DataSource dataSource, String scopeId,
                                              JsonCodec<AuthorizedActionPlan> codec, java.time.Clock clock) {
        this.scopeId = required(scopeId); this.clock = Objects.requireNonNull(clock, "clock");
        this.artifacts = new PostgresJsonArtifactRepository<>(dataSource, scopeId, "AUTHORIZED_ACTION_PLAN", codec);
    }
    @Override public void save(AuthorizedActionPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (!scopeId.equals(plan.planningRequest().workingMemory().scopeId().value())) {
            throw new SecurityException("authorized plan belongs to another persistence scope");
        }
        artifacts.save(key(plan.executionId(), plan.candidateSetId(), plan.candidateId()),
                plan.executionId(), plan.intentionId(), plan.intentionVersion(), plan, clock.instant());
    }
    @Override public Optional<AuthorizedActionPlan> find(String executionId, String candidateSetId, String candidateId) {
        Optional<AuthorizedActionPlan> result = artifacts.find(key(executionId, candidateSetId, candidateId));
        result.ifPresent(plan -> {
            if (!plan.executionId().equals(executionId) || !plan.candidateSetId().equals(candidateSetId)
                    || !plan.candidateId().equals(candidateId)
                    || !scopeId.equals(plan.planningRequest().workingMemory().scopeId().value())) {
                throw new SecurityException("persisted authorized plan identity does not match lookup binding");
            }
        });
        return result;
    }
    private static String key(String execution, String set, String candidate) {
        return required(execution) + ":" + required(set) + ":" + required(candidate);
    }
    private static String required(String value) {
        String result = Objects.requireNonNull(value, "identity").trim();
        if (result.isEmpty()) throw new IllegalArgumentException("identity cannot be blank"); return result;
    }
}
