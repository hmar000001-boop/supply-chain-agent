package dev.supplychain.agent.mechanisms.b3;

/** Deterministic bounds for explicit alternative-coalition generation. */
public record WorkspaceCoalitionCompetitionPolicy(int maximumCoalitionSize,
                                                    int maximumEvaluatedCoalitionProposals) {
    public WorkspaceCoalitionCompetitionPolicy {
        if (maximumCoalitionSize < 2 || maximumCoalitionSize > 6)
            throw new IllegalArgumentException("maximumCoalitionSize must be in [2,6]");
        if (maximumEvaluatedCoalitionProposals < 1 || maximumEvaluatedCoalitionProposals > 10_000)
            throw new IllegalArgumentException("maximumEvaluatedCoalitionProposals must be in [1,10000]");
    }

    /** Bounded engineering default: pair/triple coalitions, with a hard per-workspace evaluation cap. */
    public static WorkspaceCoalitionCompetitionPolicy standard() {
        return new WorkspaceCoalitionCompetitionPolicy(3, 512);
    }
}
