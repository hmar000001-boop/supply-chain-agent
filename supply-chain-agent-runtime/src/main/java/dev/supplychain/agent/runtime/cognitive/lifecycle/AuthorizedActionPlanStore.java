package dev.supplychain.agent.runtime.cognitive.lifecycle;

import java.util.Optional;

/** M6 reference store; M7 supplies a durable implementation bound to the approval/operation ledger. */
public interface AuthorizedActionPlanStore {
    void save(AuthorizedActionPlan plan);
    Optional<AuthorizedActionPlan> find(String executionId, String candidateSetId, String candidateId);
}
