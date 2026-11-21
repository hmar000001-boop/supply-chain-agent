package dev.supplychain.agent.runtime.cognitive.lifecycle;

import dev.supplychain.agent.mechanisms.b2.ChildResolutionResult;
import dev.supplychain.agent.mechanisms.b2.ImpasseResolutionCase;
import dev.supplychain.agent.mechanisms.b2.ParentContextSnapshot;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureDraftRequest;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations.CycleContext;
import java.time.Instant;
import java.util.Optional;

/**
 * Host-owned translation from a validated B2 result to a typed recovery draft proposal.
 * Implementations must return only registered ActionIds and opaque condition keys; narrative
 * findings are evidence only. Draft IDs must be deterministic for a given resolved case so a
 * checkpoint retry is idempotent. Returning empty means the result is not reusable as a procedure.
 */
@FunctionalInterface
public interface RecoveryProcedureDraftRequestFactory {
    Optional<RecoveryProcedureDraftRequest> create(CycleContext context, ImpasseResolutionCase resolvedCase,
            ChildResolutionResult result, ParentContextSnapshot currentParent, Instant at);
}
