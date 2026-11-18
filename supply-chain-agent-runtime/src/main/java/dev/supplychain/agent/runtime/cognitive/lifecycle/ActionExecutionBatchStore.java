package dev.supplychain.agent.runtime.cognitive.lifecycle;

import java.time.Instant;
import java.util.Optional;

/** Durable implementations must atomically create a batch and append each action result by step index. */
public interface ActionExecutionBatchStore {
    ActionExecutionBatch start(AuthorizedActionPlan plan, String batchId, String principalId, Instant at);
    ActionExecutionBatch append(String batchId, ActionExecutionBatch.ActionExecutionItem item,
                                ActionExecutionBatch.BatchStatus status, Instant at);
    ActionExecutionBatch complete(String batchId, Instant at);
    Optional<ActionExecutionBatch> find(String batchId);
}
