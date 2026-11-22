package dev.supplychain.agent.mechanisms.b4;

/** Evaluation/audit port invoked only after a versioned intention transition commits. */
@FunctionalInterface
public interface RecoveryIntentionTransitionRecorder {
    void record(RecoveryIntention before, RecoveryIntention after, TransitionKind kind, String actor);

    enum TransitionKind { RECONSIDERATION_REQUESTED, METHOD_CHANGED, ACTIVE_PLAN_BOUND, OTHER }

    static RecoveryIntentionTransitionRecorder noOp() { return (before, after, kind, actor) -> { }; }
}
