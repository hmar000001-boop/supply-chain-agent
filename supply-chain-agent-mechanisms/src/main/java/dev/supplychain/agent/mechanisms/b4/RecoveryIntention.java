package dev.supplychain.agent.mechanisms.b4;

import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import dev.supplychain.agent.domain.recovery.RecoveryObjectiveId;
import dev.supplychain.agent.domain.recovery.RecoveryConstraint;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent B4 commitment. The objective is immutable; method and active-plan references may
 * change only through a typed reconsideration transition. The lifecycle record carries its
 * rationale, continuation contract, reconsideration triggers and authority policy explicitly.
 */
public record RecoveryIntention(
        RecoveryIntentionId id,
        MemoryScopeId scopeId,
        RecoveryObjectiveId objectiveId,
        String objectiveFingerprint,
        String objectiveOwner,
        Set<RecoveryConstraint> protectedConstraints,
        RecoveryMethodReference currentMethod,
        IntentionStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String reason,
        IntentionStatus resumeStatus,
        String delegationId,
        ObjectiveSatisfactionProof completionProof,
        String rationale,
        String activePlanReference,
        ContinuationCondition continuationCondition,
        Set<ReconsiderationTrigger> reconsiderationTriggers,
        IntentionAuthorityPolicy authorityPolicy,
        IntentionTerminationReason terminationReason,
        String terminatedBy) {

    public RecoveryIntention {
        id = Objects.requireNonNull(id, "id");
        scopeId = Objects.requireNonNull(scopeId, "scopeId");
        objectiveId = Objects.requireNonNull(objectiveId, "objectiveId");
        if (objectiveFingerprint == null || !objectiveFingerprint.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("invalid objective fingerprint");
        objectiveOwner = required(objectiveOwner, "objectiveOwner");
        protectedConstraints = Set.copyOf(Objects.requireNonNull(protectedConstraints, "protectedConstraints"));
        currentMethod = Objects.requireNonNull(currentMethod, "currentMethod");
        status = Objects.requireNonNull(status, "status");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt precedes createdAt");
        reason = optional(reason);
        if ((status == IntentionStatus.SUSPENDED) != (resumeStatus != null))
            throw new IllegalArgumentException("suspended intention must preserve its previous status");
        if ((status == IntentionStatus.DELEGATED) != (delegationId != null))
            throw new IllegalArgumentException("delegation ID must exist only while delegated");
        if ((status == IntentionStatus.COMPLETED) != (completionProof != null))
            throw new IllegalArgumentException("completion proof must exist exactly for completed intention");
        if (completionProof != null && !objectiveFingerprint.equals(completionProof.objectiveFingerprint()))
            throw new IllegalArgumentException("completion proof targets a different objective");

        rationale = rationale == null || rationale.isBlank() ? "Committed objective " + objectiveId.value() : rationale.trim();
        activePlanReference = activePlanReference == null || activePlanReference.isBlank()
                ? currentMethod.candidatePlanReference() : activePlanReference.trim();
        continuationCondition = continuationCondition == null
                ? ContinuationCondition.untilObjectiveVerified() : continuationCondition;
        reconsiderationTriggers = reconsiderationTriggers == null || reconsiderationTriggers.isEmpty()
                ? Set.of(ReconsiderationTrigger.values()) : Set.copyOf(reconsiderationTriggers);
        authorityPolicy = authorityPolicy == null ? IntentionAuthorityPolicy.standard(objectiveOwner) : authorityPolicy;
        if (!authorityPolicy.objectiveOwner().equals(objectiveOwner))
            throw new SecurityException("intention authority policy belongs to a different objective owner");

        if (status == IntentionStatus.COMPLETED) {
            terminationReason = terminationReason == null ? IntentionTerminationReason.OBJECTIVE_VERIFIED : terminationReason;
            terminatedBy = required(terminatedBy == null ? completionProof.verifier() : terminatedBy, "terminatedBy");
            if (terminationReason != IntentionTerminationReason.OBJECTIVE_VERIFIED)
                throw new IllegalArgumentException("completed intention must use OBJECTIVE_VERIFIED termination reason");
        } else if (status == IntentionStatus.TERMINATED) {
            terminationReason = terminationReason == null ? IntentionTerminationReason.OPERATOR_CANCELLED : terminationReason;
            terminatedBy = required(terminatedBy, "terminatedBy");
        } else if (terminationReason != null || terminatedBy != null) {
            throw new IllegalArgumentException("non-terminal intention cannot carry termination metadata");
        }
    }

    /** Source-compatible constructor for the pre-GAP-04 persisted record shape. */
    public RecoveryIntention(RecoveryIntentionId id, MemoryScopeId scopeId, RecoveryObjectiveId objectiveId,
            String objectiveFingerprint, String objectiveOwner, Set<RecoveryConstraint> protectedConstraints,
            RecoveryMethodReference currentMethod, IntentionStatus status, long version, Instant createdAt,
            Instant updatedAt, String reason, IntentionStatus resumeStatus, String delegationId,
            ObjectiveSatisfactionProof completionProof) {
        this(id, scopeId, objectiveId, objectiveFingerprint, objectiveOwner, protectedConstraints, currentMethod,
                status, version, createdAt, updatedAt, reason, resumeStatus, delegationId, completionProof,
                null, null, null, null, null,
                status == IntentionStatus.COMPLETED ? IntentionTerminationReason.OBJECTIVE_VERIFIED
                        : status == IntentionStatus.TERMINATED ? IntentionTerminationReason.OPERATOR_CANCELLED : null,
                status == IntentionStatus.COMPLETED && completionProof != null ? completionProof.verifier()
                        : status == IntentionStatus.TERMINATED ? "operator" : null);
    }

    public static RecoveryIntention create(RecoveryObjective objective, MemoryScopeId scope, String owner,
            RecoveryMethodReference method, Instant at) {
        return create(RecoveryIntentionId.random(), objective, scope, owner, method,
                objective.description(), ContinuationCondition.untilObjectiveVerified(), Set.of(ReconsiderationTrigger.values()),
                IntentionAuthorityPolicy.standard(owner), at);
    }

    public static RecoveryIntention create(RecoveryIntentionId id, RecoveryObjective objective, MemoryScopeId scope,
            String owner, RecoveryMethodReference method, Instant at) {
        return create(id, objective, scope, owner, method, objective.description(),
                ContinuationCondition.untilObjectiveVerified(), Set.of(ReconsiderationTrigger.values()),
                IntentionAuthorityPolicy.standard(owner), at);
    }

    public static RecoveryIntention create(RecoveryIntentionId id, RecoveryObjective objective, MemoryScopeId scope,
            String owner, RecoveryMethodReference method, String rationale,
            ContinuationCondition continuationCondition, Set<ReconsiderationTrigger> triggers,
            IntentionAuthorityPolicy authorityPolicy, Instant at) {
        Objects.requireNonNull(objective, "objective");
        Objects.requireNonNull(at, "at");
        if (objective.status() != dev.supplychain.agent.domain.recovery.RecoveryObjectiveStatus.ACTIVE)
            throw new IllegalStateException("only an active recovery objective can create an intention");
        return new RecoveryIntention(Objects.requireNonNull(id, "id"), scope, objective.id(),
                ObjectiveFingerprint.from(objective).value(), owner, objective.hardConstraints(), method,
                IntentionStatus.ACTIVE, 0, at, at, null, null, null, null, rationale,
                method.candidatePlanReference(), continuationCondition, triggers, authorityPolicy, null, null);
    }

    public RecoveryIntention requestReconsideration(String why, Instant at) {
        return requestReconsideration(ReconsiderationTrigger.OPERATOR_REQUEST, authorityPolicy.decisionGovernor(), why, at);
    }

    public RecoveryIntention requestReconsideration(ReconsiderationTrigger trigger, String actor, String why, Instant at) {
        requireActive();
        Objects.requireNonNull(trigger, "trigger");
        if (!reconsiderationTriggers.contains(trigger))
            throw new SecurityException("reconsideration trigger is not permitted by the intention contract: " + trigger);
        authorityPolicy.requireTransitionActor(actor);
        return revise(IntentionStatus.RECONSIDERATION_PENDING, at, why + " [trigger=" + trigger + "]",
                null, null, null, activePlanReference, null, null);
    }

    public RecoveryIntention changeMethod(RecoveryMethodReference method, String assertedObjectiveFingerprint, Instant at) {
        return changeMethod(method, assertedObjectiveFingerprint, authorityPolicy.decisionGovernor(), at);
    }

    public RecoveryIntention changeMethod(RecoveryMethodReference method, String assertedObjectiveFingerprint,
            String actor, Instant at) {
        if (status != IntentionStatus.RECONSIDERATION_PENDING)
            throw new IllegalStateException("method can change only after reconsideration is requested");
        if (!objectiveFingerprint.equals(assertedObjectiveFingerprint))
            throw new SecurityException("method change cannot alter the protected objective");
        authorityPolicy.requireTransitionActor(actor);
        RecoveryMethodReference nextMethod = Objects.requireNonNull(method, "method");
        return revise(IntentionStatus.ACTIVE, at, "method changed by " + actor, null, null, nextMethod,
                nextMethod.candidatePlanReference(), null, null);
    }

    public RecoveryIntention bindActivePlan(String planReference, String actor, Instant at) {
        requireActive();
        authorityPolicy.requireTransitionActor(actor);
        String nextPlan = required(planReference, "planReference");
        if (at.isBefore(updatedAt)) throw new IllegalArgumentException("intention time cannot move backwards");
        return new RecoveryIntention(id, scopeId, objectiveId, objectiveFingerprint, objectiveOwner,
                protectedConstraints, currentMethod, status, version + 1, createdAt, at,
                "active plan bound by " + actor, null, null, null, rationale, nextPlan,
                continuationCondition, reconsiderationTriggers, authorityPolicy, null, null);
    }

    public boolean continuationExpired(Instant at, boolean objectiveVerified, boolean authorityReleased) {
        return continuationCondition.continuationExpired(at, objectiveVerified, authorityReleased);
    }

    public RecoveryIntention suspend(String why, Instant at) {
        return suspend(why, authorityPolicy.decisionGovernor(), at);
    }

    public RecoveryIntention suspend(String why, String actor, Instant at) {
        if (status != IntentionStatus.ACTIVE && status != IntentionStatus.RECONSIDERATION_PENDING)
            throw new IllegalStateException("intention cannot be suspended from " + status);
        authorityPolicy.requireTransitionActor(actor);
        return revise(IntentionStatus.SUSPENDED, at, why, status, null, null, activePlanReference, null, null);
    }

    public RecoveryIntention resume(Instant at) { return resume(authorityPolicy.decisionGovernor(), at); }

    public RecoveryIntention resume(String actor, Instant at) {
        if (status != IntentionStatus.SUSPENDED) throw new IllegalStateException("intention is not suspended");
        authorityPolicy.requireTransitionActor(actor);
        return revise(resumeStatus, at, "resumed by " + actor, null, null, null, activePlanReference, null, null);
    }

    public RecoveryIntention delegate(String childCaseId, Instant at) {
        return delegate(childCaseId, authorityPolicy.decisionGovernor(), at);
    }

    public RecoveryIntention delegate(String childCaseId, String actor, Instant at) {
        if (status != IntentionStatus.ACTIVE) throw new IllegalStateException("only active intention can delegate a bounded investigation");
        authorityPolicy.requireTransitionActor(actor);
        return revise(IntentionStatus.DELEGATED, at, "bounded investigation delegated by " + actor,
                null, required(childCaseId, "childCaseId"), null, activePlanReference, null, null);
    }

    public RecoveryIntention returnFromDelegation(String childCaseId, DelegationReturn result, Instant at) {
        return returnFromDelegation(childCaseId, result, authorityPolicy.decisionGovernor(), at);
    }

    public RecoveryIntention returnFromDelegation(String childCaseId, DelegationReturn result,
            String actor, Instant at) {
        Objects.requireNonNull(result, "result");
        if (status != IntentionStatus.DELEGATED || !delegationId.equals(childCaseId))
            throw new IllegalStateException("delegation return does not match active child");
        authorityPolicy.requireTransitionActor(actor);
        IntentionStatus next = result == DelegationReturn.NO_METHOD_CHANGE
                ? IntentionStatus.ACTIVE : IntentionStatus.RECONSIDERATION_PENDING;
        if (next == IntentionStatus.RECONSIDERATION_PENDING
                && !reconsiderationTriggers.contains(ReconsiderationTrigger.DELEGATED_INVESTIGATION_RETURNED)) {
            throw new SecurityException("delegation return is not an allowed reconsideration trigger");
        }
        return revise(next, at, "delegation returned by " + actor + ": " + result, null, null, null,
                activePlanReference, null, null);
    }

    public RecoveryIntention complete(ObjectiveSatisfactionProof proof, Instant at) {
        Objects.requireNonNull(proof, "proof");
        Objects.requireNonNull(at, "at");
        if (status != IntentionStatus.ACTIVE) throw new IllegalStateException("only active intention can complete");
        if (!objectiveFingerprint.equals(proof.objectiveFingerprint()))
            throw new SecurityException("completion proof does not match protected objective");
        if (proof.verifiedAt().isBefore(updatedAt) || proof.verifiedAt().isAfter(at) || at.isBefore(updatedAt))
            throw new IllegalArgumentException("completion proof time is outside intention lifecycle");
        return new RecoveryIntention(id, scopeId, objectiveId, objectiveFingerprint, objectiveOwner,
                protectedConstraints, currentMethod, IntentionStatus.COMPLETED, version + 1, createdAt, at,
                "objective verified", null, null, proof, rationale, activePlanReference,
                continuationCondition, reconsiderationTriggers, authorityPolicy,
                IntentionTerminationReason.OBJECTIVE_VERIFIED, proof.verifier());
    }

    public RecoveryIntention terminate(String actor, String why, Instant at) {
        return terminate(actor, why, IntentionTerminationReason.OPERATOR_CANCELLED, at);
    }

    public RecoveryIntention terminate(String actor, String why, IntentionTerminationReason termination,
            Instant at) {
        authorityPolicy.requireTerminationActor(actor);
        return terminalize(actor, why, termination, at);
    }

    /** Called only after the enclosing runtime has verified its independent host termination authority. */
    public RecoveryIntention terminateAfterHostAuthorization(String actor, String why,
            IntentionTerminationReason termination, Instant at) {
        if (!authorityPolicy.terminationRequiresIndependentHostAuthorization())
            throw new SecurityException("intention policy does not require the independent host authorization boundary");
        return terminalize(actor, why, termination, at);
    }

    private RecoveryIntention terminalize(String actor, String why, IntentionTerminationReason termination, Instant at) {
        Objects.requireNonNull(at, "at");
        if (status == IntentionStatus.COMPLETED || status == IntentionStatus.TERMINATED)
            throw new IllegalStateException("intention is already terminal");
        if (at.isBefore(updatedAt)) throw new IllegalArgumentException("intention time cannot move backwards");
        if (termination == IntentionTerminationReason.OBJECTIVE_VERIFIED)
            throw new IllegalArgumentException("use evidence-backed completion for OBJECTIVE_VERIFIED");
        return new RecoveryIntention(id, scopeId, objectiveId, objectiveFingerprint, objectiveOwner,
                protectedConstraints, currentMethod, IntentionStatus.TERMINATED, version + 1, createdAt, at,
                required(actor, "actor") + ": " + required(why, "reason"), null, null, null,
                rationale, activePlanReference, continuationCondition, reconsiderationTriggers,
                authorityPolicy, Objects.requireNonNull(termination, "termination"), actor);
    }

    private void requireActive() {
        if (status != IntentionStatus.ACTIVE) throw new IllegalStateException("intention is not active");
    }

    private RecoveryIntention revise(IntentionStatus next, Instant at, String why,
            IntentionStatus resume, String delegation, RecoveryMethodReference method,
            String nextPlanReference, IntentionTerminationReason nextTermination, String nextTerminatedBy) {
        String normalizedWhy = required(why, "reason");
        if (at.isBefore(updatedAt)) throw new IllegalArgumentException("intention time cannot move backwards");
        return new RecoveryIntention(id, scopeId, objectiveId, objectiveFingerprint, objectiveOwner,
                protectedConstraints, method == null ? currentMethod : method, next, version + 1,
                createdAt, at, normalizedWhy, resume, delegation, null, rationale,
                nextPlanReference, continuationCondition, reconsiderationTriggers, authorityPolicy,
                nextTermination, nextTerminatedBy);
    }

    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
