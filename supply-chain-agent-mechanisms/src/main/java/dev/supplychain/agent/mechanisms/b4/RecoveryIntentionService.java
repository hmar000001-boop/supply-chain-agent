package dev.supplychain.agent.mechanisms.b4;

import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Versioned B4 intention lifecycle with explicit trigger and actor checks. */
public final class RecoveryIntentionService {
    private final RecoveryIntentionRepository repository;
    private final RecoveryIntentionTransitionRecorder transitionRecorder;

    public RecoveryIntentionService(RecoveryIntentionRepository repository) {
        this(repository, RecoveryIntentionTransitionRecorder.noOp());
    }

    public RecoveryIntentionService(RecoveryIntentionRepository repository,
            RecoveryIntentionTransitionRecorder transitionRecorder) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.transitionRecorder = Objects.requireNonNull(transitionRecorder, "transitionRecorder");
    }

    public RecoveryIntention create(RecoveryObjective objective, MemoryScopeId scope, String owner,
            RecoveryMethodReference method, Instant at) {
        return create(RecoveryIntentionId.random(), objective, scope, owner, method, at);
    }

    public RecoveryIntention create(RecoveryIntentionId id, RecoveryObjective objective, MemoryScopeId scope,
            String owner, RecoveryMethodReference method, Instant at) {
        return create(id, objective, scope, owner, method, objective.description(),
                ContinuationCondition.untilObjectiveVerified(), Set.of(ReconsiderationTrigger.values()),
                IntentionAuthorityPolicy.standard(owner), at);
    }

    public RecoveryIntention create(RecoveryIntentionId id, RecoveryObjective objective, MemoryScopeId scope,
            String owner, RecoveryMethodReference method, String rationale,
            ContinuationCondition continuation, Set<ReconsiderationTrigger> triggers,
            IntentionAuthorityPolicy authorityPolicy, Instant at) {
        RecoveryIntention value = RecoveryIntention.create(id, objective, scope, owner, method, rationale,
                continuation, triggers, authorityPolicy, at);
        repository.saveNew(value);
        return value;
    }

    public java.util.Optional<RecoveryIntention> find(RecoveryIntentionId id) { return repository.findById(id); }
    public RecoveryIntention get(RecoveryIntentionId id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("unknown intention: " + id));
    }

    public RecoveryIntention requestReconsideration(RecoveryIntentionId id, long version, String reason, Instant at) {
        return requestReconsideration(id, version, ReconsiderationTrigger.OPERATOR_REQUEST,
                get(id).authorityPolicy().decisionGovernor(), reason, at);
    }

    public RecoveryIntention requestReconsideration(RecoveryIntentionId id, long version,
            ReconsiderationTrigger trigger, String actor, String reason, Instant at) {
        return update(id, version, value -> value.requestReconsideration(trigger, actor, reason, at));
    }

    public RecoveryIntention changeMethod(RecoveryIntentionId id, long version, RecoveryMethodReference method,
            String objectiveFingerprint, Instant at) {
        return changeMethod(id, version, method, objectiveFingerprint, get(id).authorityPolicy().decisionGovernor(), at);
    }

    public RecoveryIntention changeMethod(RecoveryIntentionId id, long version, RecoveryMethodReference method,
            String objectiveFingerprint, String actor, Instant at) {
        return update(id, version, value -> value.changeMethod(method, objectiveFingerprint, actor, at));
    }

    public RecoveryIntention bindActivePlan(RecoveryIntentionId id, long version, String planReference,
            String actor, Instant at) {
        return update(id, version, value -> value.bindActivePlan(planReference, actor, at));
    }

    public RecoveryIntention suspend(RecoveryIntentionId id, long version, String reason, Instant at) {
        return suspend(id, version, get(id).authorityPolicy().decisionGovernor(), reason, at);
    }

    public RecoveryIntention suspend(RecoveryIntentionId id, long version, String actor, String reason, Instant at) {
        return update(id, version, value -> value.suspend(reason, actor, at));
    }

    public RecoveryIntention resume(RecoveryIntentionId id, long version, Instant at) {
        return resume(id, version, get(id).authorityPolicy().decisionGovernor(), at);
    }

    public RecoveryIntention resume(RecoveryIntentionId id, long version, String actor, Instant at) {
        return update(id, version, value -> value.resume(actor, at));
    }

    public RecoveryIntention delegate(RecoveryIntentionId id, long version, String caseId, Instant at) {
        return delegate(id, version, get(id).authorityPolicy().decisionGovernor(), caseId, at);
    }

    public RecoveryIntention delegate(RecoveryIntentionId id, long version, String actor, String caseId, Instant at) {
        return update(id, version, value -> value.delegate(caseId, actor, at));
    }

    public RecoveryIntention returnFromDelegation(RecoveryIntentionId id, long version, String caseId,
            DelegationReturn result, Instant at) {
        return returnFromDelegation(id, version, caseId, result, get(id).authorityPolicy().decisionGovernor(), at);
    }

    public RecoveryIntention returnFromDelegation(RecoveryIntentionId id, long version, String caseId,
            DelegationReturn result, String actor, Instant at) {
        return update(id, version, value -> value.returnFromDelegation(caseId, result, actor, at));
    }

    public RecoveryIntention complete(RecoveryIntentionId id, long version, ObjectiveSatisfactionProof proof, Instant at) {
        return update(id, version, value -> value.complete(proof, at));
    }

    public RecoveryIntention terminate(RecoveryIntentionId id, long version, String actor, String reason, Instant at) {
        return terminate(id, version, actor, reason, IntentionTerminationReason.OPERATOR_CANCELLED, at);
    }

    public RecoveryIntention terminate(RecoveryIntentionId id, long version, String actor, String reason,
            IntentionTerminationReason terminationReason, Instant at) {
        return update(id, version, value -> value.terminate(actor, reason, terminationReason, at));
    }

    /** Runtime-only path after its host authorization port has authenticated and authorized the actor. */
    public RecoveryIntention terminateAfterHostAuthorization(RecoveryIntentionId id, long version,
            String actor, String reason, IntentionTerminationReason terminationReason, Instant at) {
        return update(id, version, value -> value.terminateAfterHostAuthorization(actor, reason, terminationReason, at));
    }

    private RecoveryIntention update(RecoveryIntentionId id, long expected, UnaryOperator<RecoveryIntention> operation) {
        RecoveryIntention current = get(Objects.requireNonNull(id, "id"));
        if (current.version() != expected) throw new IllegalStateException("intention version conflict");
        RecoveryIntention next = operation.apply(current);
        if (!repository.compareAndSet(id, expected, next)) throw new IllegalStateException("intention changed concurrently");
        transitionRecorder.record(current, next, transitionKind(current, next));
        return next;
    }
}
