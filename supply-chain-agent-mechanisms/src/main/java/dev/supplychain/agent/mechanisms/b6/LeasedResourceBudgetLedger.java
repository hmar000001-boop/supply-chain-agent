package dev.supplychain.agent.mechanisms.b6;

import java.time.Instant;
import java.util.Optional;

/**
 * Resource-budget ledger with ownership leases, fencing tokens and crash recovery.
 * The legacy reserve/commit API remains available for deterministic meters; production guarded
 * invocations must use this interface so a process restart cannot blindly repeat started work.
 */
public interface LeasedResourceBudgetLedger extends ResourceBudgetLedger {

    BudgetReservationLease acquireLease(String executionId, String reservationId, String ownerId,
                                        ResourceUsage requested, Instant leaseExpiresAt, Instant at);

    BudgetReservationLease markOperationStarted(String executionId, String reservationId, String ownerId,
                                                long fencingToken, Instant at);

    BudgetReservationLease renewLease(String executionId, String reservationId, String ownerId,
                                       long fencingToken, Instant leaseExpiresAt, Instant at);

    ResourceBudgetSnapshot commitOwned(String executionId, String reservationId, String ownerId,
                                       long fencingToken, ResourceUsage actual, Instant at);

    ResourceBudgetSnapshot releaseOwnedBeforeStart(String executionId, String reservationId, String ownerId,
                                                    long fencingToken, Instant at);

    /**
     * Recovers at most {@code maximum} expired reservations for one execution. Unstarted work is
     * safely released and may be reacquired with a higher fencing token. Started work is never
     * replayed: every finite budget dimension is charged up to the remaining capacity after
     * preserving reservations still held by other active owners. Dimensions configured as
     * unbounded remain outside the ledger's ability to conservatively cap an unknown outcome.
     */
    int recoverExpiredReservations(String executionId, Instant at, int maximum);

    Optional<BudgetReservationLease> findLease(String executionId, String reservationId);
}
