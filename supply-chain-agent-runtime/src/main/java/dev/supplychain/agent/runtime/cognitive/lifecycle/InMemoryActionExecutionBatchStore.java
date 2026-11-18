package dev.supplychain.agent.runtime.cognitive.lifecycle;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe reference adapter; M7 replaces it with a transactional database implementation. */
public final class InMemoryActionExecutionBatchStore implements ActionExecutionBatchStore {
    private final ConcurrentHashMap<String, ActionExecutionBatch> batches = new ConcurrentHashMap<>();

    @Override public ActionExecutionBatch start(AuthorizedActionPlan plan, String batchId, String principalId, Instant at) {
        Objects.requireNonNull(plan, "plan");
        var created = new ActionExecutionBatch(batchId, plan.executionId(), principalId, plan.candidateSetId(), plan.candidateId(),
                plan.fingerprint(), ActionExecutionBatch.BatchStatus.STARTED, java.util.List.of(), at, at);
        var existing = batches.putIfAbsent(batchId, created);
        if (existing != null && (!existing.executionId().equals(plan.executionId())
                || !existing.principalId().equals(principalId)
                || !existing.candidateSetId().equals(plan.candidateSetId())
                || !existing.candidateId().equals(plan.candidateId())
                || !existing.planFingerprint().equals(plan.fingerprint()))) {
            throw new SecurityException("action batch identity was reused for a different plan");
        }
        return existing == null ? created : existing;
    }

    @Override public ActionExecutionBatch append(String batchId, ActionExecutionBatch.ActionExecutionItem item,
                                                 ActionExecutionBatch.BatchStatus status, Instant at) {
        Objects.requireNonNull(item, "item"); Objects.requireNonNull(status, "status"); Objects.requireNonNull(at, "at");
        return batches.compute(batchId, (key, current) -> {
            if (current == null) throw new IllegalStateException("unknown action batch: " + batchId);
            var existing = current.items().stream().filter(value -> value.stepIndex() == item.stepIndex()).findFirst();
            if (existing.isPresent()) {
                if (!existing.get().equals(item)) throw new SecurityException("action step was recorded with conflicting result");
                return current;
            }
            if (current.status() == ActionExecutionBatch.BatchStatus.SUCCEEDED
                    || current.status() == ActionExecutionBatch.BatchStatus.REJECTED
                    || current.status() == ActionExecutionBatch.BatchStatus.FAILED) {
                throw new IllegalStateException("terminal action batch cannot accept another step");
            }
            if (at.isBefore(current.updatedAt())) throw new IllegalArgumentException("batch update time cannot move backwards");
            var items = new ArrayList<>(current.items());
            items.add(item);
            items.sort(Comparator.comparingInt(ActionExecutionBatch.ActionExecutionItem::stepIndex));
            return new ActionExecutionBatch(current.batchId(), current.executionId(), current.principalId(), current.candidateSetId(),
                    current.candidateId(), current.planFingerprint(), status, items, current.createdAt(), at);
        });
    }


    @Override public ActionExecutionBatch complete(String batchId, Instant at) {
        Objects.requireNonNull(at, "at");
        return batches.compute(batchId, (key, current) -> {
            if (current == null) throw new IllegalStateException("unknown action batch: " + batchId);
            if (at.isBefore(current.updatedAt())) throw new IllegalArgumentException("batch update time cannot move backwards");
            if (current.status() == ActionExecutionBatch.BatchStatus.SUCCEEDED) return current;
            if (current.status() != ActionExecutionBatch.BatchStatus.STARTED) {
                throw new IllegalStateException("only a fully started batch can be completed");
            }
            return new ActionExecutionBatch(current.batchId(), current.executionId(), current.principalId(), current.candidateSetId(),
                    current.candidateId(), current.planFingerprint(), ActionExecutionBatch.BatchStatus.SUCCEEDED,
                    current.items(), current.createdAt(), at);
        });
    }

    @Override public Optional<ActionExecutionBatch> find(String batchId) { return Optional.ofNullable(batches.get(batchId)); }
}
