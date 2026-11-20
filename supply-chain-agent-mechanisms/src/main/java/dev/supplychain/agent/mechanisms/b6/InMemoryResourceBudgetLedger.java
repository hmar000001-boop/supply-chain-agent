package dev.supplychain.agent.mechanisms.b6;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Synchronized reference/test ledger; production hosts inject the PostgreSQL leased ledger. */
public final class InMemoryResourceBudgetLedger implements LeasedResourceBudgetLedger {
    private static final class Account {
        final ResourceBudget budget;
        final EnumMap<ResourceUnit, Long> consumed = new EnumMap<>(ResourceUnit.class);
        final Map<String, BudgetReservationLease> reservations = new HashMap<>();
        Account(ResourceBudget budget) { this.budget = budget; }
    }

    private final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();

    @Override public void register(String executionId, ResourceBudget budget) {
        String id = required(executionId, "executionId");
        if (accounts.putIfAbsent(id, new Account(Objects.requireNonNull(budget, "budget"))) != null) {
            throw new IllegalStateException("budget already registered for execution " + id);
        }
    }

    @Override public BudgetReservation reserve(String executionId, String reservationId,
                                               ResourceUsage requested, Instant at) {
        Account account = account(executionId);
        synchronized (account) {
            requireBeforeDeadline(account, at);
            BudgetReservationLease existing = account.reservations.get(reservationId);
            if (existing != null) {
                if (!existing.reserved().equals(requested)) throw new IllegalStateException("reservation ID reused with different amount");
                if (!existing.ownerId().isBlank()) throw new SecurityException("owned reservation requires its fencing token");
                if (existing.status() == ReservationStatus.RELEASED) throw new IllegalStateException("released reservation ID cannot be reused");
                return toLegacy(existing);
            }
            ensureAvailable(account, requested);
            BudgetReservationLease created = new BudgetReservationLease(reservationId, executionId, requested, at,
                    "", 0, null, null, ReservationStatus.ACTIVE, null, BudgetReservationRecovery.NONE);
            account.reservations.put(reservationId, created);
            return toLegacy(created);
        }
    }

    @Override public ResourceBudgetSnapshot commit(String executionId, String reservationId,
                                                   ResourceUsage actual, Instant at) {
        Account account = account(executionId);
        synchronized (account) {
            BudgetReservationLease current = requireReservation(account, reservationId);
            if (!current.ownerId().isBlank()) throw new SecurityException("owned reservation requires its fencing token");
            return commitLocked(executionId, account, current, actual, at, BudgetReservationRecovery.NONE);
        }
    }

    @Override public ResourceBudgetSnapshot release(String executionId, String reservationId, Instant at) {
        Account account = account(executionId);
        synchronized (account) {
            BudgetReservationLease current = requireReservation(account, reservationId);
            if (!current.ownerId().isBlank()) throw new SecurityException("owned reservation requires its fencing token");
            if (current.status() == ReservationStatus.RELEASED) return snapshotLocked(executionId, account, at);
            if (current.status() != ReservationStatus.ACTIVE) throw new IllegalStateException("committed reservation cannot be released");
            account.reservations.put(reservationId, copy(current, current.ownerId(), current.fencingToken(),
                    current.leaseExpiresAt(), current.operationStartedAt(), ReservationStatus.RELEASED,
                    null, BudgetReservationRecovery.EXPLICITLY_RELEASED));
            return snapshotLocked(executionId, account, at);
        }
    }

    @Override public ResourceBudgetSnapshot snapshot(String executionId, Instant at) {
        Account account = account(executionId);
        synchronized (account) {
            recoverExpiredLocked(executionId, account, at, Integer.MAX_VALUE);
            return snapshotLocked(executionId, account, at);
        }
    }

    @Override public BudgetReservationLease acquireLease(String executionId, String reservationId, String ownerId,
                                                          ResourceUsage requested, Instant leaseExpiresAt, Instant at) {
        String execution = required(executionId, "executionId");
        String reservation = required(reservationId, "reservationId");
        String owner = required(ownerId, "ownerId");
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
        Account account = account(execution);
        synchronized (account) {
            requireBeforeDeadline(account, at);
            if (!leaseExpiresAt.isAfter(at)) throw new IllegalArgumentException("lease must expire after acquisition time");
            recoverExpiredLocked(execution, account, at, Integer.MAX_VALUE);
            BudgetReservationLease existing = account.reservations.get(reservation);
            if (existing != null) {
                if (!existing.reserved().equals(requested)) throw new SecurityException("reservation identity reused with a different resource amount");
                if (existing.status() == ReservationStatus.COMMITTED) return existing;
                if (existing.status() == ReservationStatus.ACTIVE) {
                    throw new BudgetReservationInUseException("reservation is still owned by " + existing.ownerId());
                }
                if (existing.recovery() != BudgetReservationRecovery.EXPIRED_BEFORE_START
                        || existing.operationStarted()) {
                    throw new IllegalStateException("released reservation is not eligible for safe lease reclamation");
                }
                BudgetReservationLease reclaimed = copy(existing, owner, existing.fencingToken() + 1,
                        leaseExpiresAt, null, ReservationStatus.ACTIVE, null, BudgetReservationRecovery.NONE);
                account.reservations.put(reservation, reclaimed);
                return reclaimed;
            }
            ensureAvailable(account, requested);
            BudgetReservationLease created = new BudgetReservationLease(reservation, execution, requested, at,
                    owner, 1L, leaseExpiresAt, null, ReservationStatus.ACTIVE, null, BudgetReservationRecovery.NONE);
            account.reservations.put(reservation, created);
            return created;
        }
    }

    @Override public BudgetReservationLease markOperationStarted(String executionId, String reservationId,
                                                                  String ownerId, long fencingToken, Instant at) {
        Account account = account(executionId);
        synchronized (account) {
            BudgetReservationLease current = requireOwned(account, reservationId, ownerId, fencingToken, at);
            if (current.status() != ReservationStatus.ACTIVE || current.operationStarted()) {
                throw new SecurityException("reservation operation start is not a valid first transition");
            }
            BudgetReservationLease started = copy(current, current.ownerId(), current.fencingToken(),
                    current.leaseExpiresAt(), at, ReservationStatus.ACTIVE, null, BudgetReservationRecovery.NONE);
            account.reservations.put(reservationId, started);
            return started;
        }
    }

    @Override public BudgetReservationLease renewLease(String executionId, String reservationId, String ownerId,
                                                        long fencingToken, Instant leaseExpiresAt, Instant at) {
        Account account = account(executionId);
        synchronized (account) {
            BudgetReservationLease current = requireOwned(account, reservationId, ownerId, fencingToken, at);
            if (current.status() != ReservationStatus.ACTIVE || !current.operationStarted()) {
                throw new SecurityException("only a started active reservation can renew its lease");
            }
            if (!leaseExpiresAt.isAfter(at) || !leaseExpiresAt.isAfter(current.leaseExpiresAt())) {
                throw new IllegalArgumentException("renewed lease must extend beyond the current lease");
            }
            BudgetReservationLease renewed = copy(current, current.ownerId(), current.fencingToken(),
                    leaseExpiresAt, current.operationStartedAt(), current.status(), null, current.recovery());
            account.reservations.put(reservationId, renewed);
            return renewed;
        }
    }

    @Override public ResourceBudgetSnapshot commitOwned(String executionId, String reservationId, String ownerId,
                                                         long fencingToken, ResourceUsage actual, Instant at) {
        Account account = account(executionId);
        synchronized (account) {
            BudgetReservationLease current = requireOwned(account, reservationId, ownerId, fencingToken, at);
            if (!current.operationStarted()) throw new SecurityException("cannot commit a reservation before operation start");
            return commitLocked(executionId, account, current, actual, at, BudgetReservationRecovery.NONE);
        }
    }

    @Override public ResourceBudgetSnapshot releaseOwnedBeforeStart(String executionId, String reservationId,
                                                                    String ownerId, long fencingToken, Instant at) {
        Account account = account(executionId);
        synchronized (account) {
            BudgetReservationLease current = requireOwned(account, reservationId, ownerId, fencingToken, at);
            if (current.operationStarted()) throw new SecurityException("started work cannot be released");
            account.reservations.put(reservationId, copy(current, current.ownerId(), current.fencingToken() + 1,
                    current.leaseExpiresAt(), null, ReservationStatus.RELEASED, null,
                    BudgetReservationRecovery.EXPLICITLY_RELEASED));
            return snapshotLocked(executionId, account, at);
        }
    }

    @Override public int recoverExpiredReservations(String executionId, Instant at, int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("maximum must be positive");
        Account account = account(executionId);
        synchronized (account) { return recoverExpiredLocked(executionId, account, at, maximum); }
    }

    @Override public Optional<BudgetReservationLease> findLease(String executionId, String reservationId) {
        Account account = account(executionId);
        synchronized (account) { return Optional.ofNullable(account.reservations.get(reservationId)); }
    }

    private int recoverExpiredLocked(String executionId, Account account, Instant at, int maximum) {
        Objects.requireNonNull(at, "at");
        int recovered = 0;
        // Reclaim all expired reservations that never started before conservatively charging
        // started unknown-outcome work. Otherwise an expired, unstarted lease encountered later
        // in iteration could be incorrectly preserved as an active capacity hold.
        var expired = new java.util.ArrayList<>(account.reservations.entrySet());
        expired.sort(Comparator
                .comparing((Map.Entry<String, BudgetReservationLease> entry) -> entry.getValue().operationStarted())
                .thenComparing(entry -> entry.getValue().leaseExpiresAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Map.Entry::getKey));
        for (var entry : expired) {
            if (recovered >= maximum) break;
            BudgetReservationLease current = entry.getValue();
            if (current.status() != ReservationStatus.ACTIVE || current.leaseExpiresAt() == null
                    || current.leaseExpiresAt().isAfter(at)) continue;
            if (!current.operationStarted()) {
                account.reservations.put(entry.getKey(), copy(current, current.ownerId(), current.fencingToken() + 1,
                        current.leaseExpiresAt(), null, ReservationStatus.RELEASED, null,
                        BudgetReservationRecovery.EXPIRED_BEFORE_START));
            } else {
                ResourceUsage conservative = conservativeCharge(account, current);
                addConsumed(account, conservative);
                account.reservations.put(entry.getKey(), copy(current, current.ownerId(), current.fencingToken() + 1,
                        current.leaseExpiresAt(), current.operationStartedAt(), ReservationStatus.COMMITTED,
                        conservative, BudgetReservationRecovery.EXPIRED_AFTER_START_CONSERVATIVELY_CHARGED));
            }
            recovered++;
        }
        return recovered;
    }

    private static ResourceUsage conservativeCharge(Account account, BudgetReservationLease recovering) {
        EnumMap<ResourceUnit, Long> charge = new EnumMap<>(ResourceUnit.class);
        for (ResourceUnit unit : ResourceUnit.values()) {
            long limit = account.budget.limit(unit);
            if (limit == Long.MAX_VALUE) {
                // An unbounded dimension has no finite ceiling to charge against. Preserve the
                // reservation estimate, but do not claim this is a conservative upper bound.
                charge.put(unit, recovering.reserved().amount(unit));
                continue;
            }
            long consumed = account.consumed.getOrDefault(unit, 0L);
            long otherHeld = Math.max(0L, reservedFor(account, unit) - recovering.reserved().amount(unit));
            long usedAndOtherHeld = saturatedAdd(consumed, otherHeld);
            charge.put(unit, Math.max(0L, limit - Math.min(limit, usedAndOtherHeld)));
        }
        return new ResourceUsage(charge);
    }

    private ResourceBudgetSnapshot commitLocked(String executionId, Account account, BudgetReservationLease current,
                                                 ResourceUsage actual, Instant at, BudgetReservationRecovery recovery) {
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(at, "at");
        if (current.status() == ReservationStatus.COMMITTED) {
            if (!current.actualUsage().equals(actual)) throw new IllegalStateException("committed reservation cannot be charged with different actual usage");
            return snapshotLocked(executionId, account, at);
        }
        if (current.status() != ReservationStatus.ACTIVE) throw new IllegalStateException("released reservation cannot be committed");
        addConsumed(account, actual);
        account.reservations.put(current.reservationId(), copy(current, current.ownerId(), current.fencingToken(),
                current.leaseExpiresAt(), current.operationStartedAt(), ReservationStatus.COMMITTED, actual, recovery));
        return snapshotLocked(executionId, account, at);
    }

    private static void addConsumed(Account account, ResourceUsage actual) {
        for (ResourceUnit unit : ResourceUnit.values()) {
            account.consumed.compute(unit, (ignored, current) -> saturatedAdd(current == null ? 0L : current, actual.amount(unit)));
        }
    }

    private static void ensureAvailable(Account account, ResourceUsage requested) {
        for (ResourceUnit unit : ResourceUnit.values()) {
            long limit = account.budget.limit(unit);
            if (limit == Long.MAX_VALUE) continue;
            long consumed = account.consumed.getOrDefault(unit, 0L);
            if (consumed > limit) throw new ResourceBudgetExceededException("resource budget already exceeded for " + unit);
            long held = reservedFor(account, unit);
            long available = limit - consumed;
            if (held > available || requested.amount(unit) > available - held) {
                throw new ResourceBudgetExceededException("resource budget exceeded for " + unit);
            }
        }
    }

    private static long reservedFor(Account account, ResourceUnit unit) {
        long total = 0L;
        for (BudgetReservationLease reservation : account.reservations.values()) {
            if (reservation.status() == ReservationStatus.ACTIVE) total = saturatedAdd(total, reservation.reserved().amount(unit));
        }
        return total;
    }

    private static ResourceBudgetSnapshot snapshotLocked(String id, Account account, Instant at) {
        EnumMap<ResourceUnit, Long> reserved = new EnumMap<>(ResourceUnit.class);
        for (BudgetReservationLease reservation : account.reservations.values()) {
            if (reservation.status() != ReservationStatus.ACTIVE) continue;
            for (ResourceUnit unit : ResourceUnit.values()) {
                reserved.compute(unit, (ignored, current) -> saturatedAdd(current == null ? 0L : current,
                        reservation.reserved().amount(unit)));
            }
        }
        return new ResourceBudgetSnapshot(id, account.budget, account.consumed, reserved, at);
    }

    private static BudgetReservationLease requireOwned(Account account, String id, String owner,
                                                        long token, Instant at) {
        BudgetReservationLease current = requireReservation(account, id);
        if (current.status() != ReservationStatus.ACTIVE || !current.ownerId().equals(owner)
                || current.fencingToken() != token || current.leaseExpiresAt() == null
                || !current.leaseExpiresAt().isAfter(at)) {
            throw new SecurityException("budget reservation owner or fencing token is stale");
        }
        return current;
    }

    private static BudgetReservationLease requireReservation(Account account, String id) {
        BudgetReservationLease value = account.reservations.get(id);
        if (value == null) throw new IllegalStateException("reservation not found: " + id);
        return value;
    }

    private static BudgetReservationLease copy(BudgetReservationLease source, String owner, long token,
                                               Instant leaseUntil, Instant startedAt, ReservationStatus status,
                                               ResourceUsage actual, BudgetReservationRecovery recovery) {
        return new BudgetReservationLease(source.reservationId(), source.executionId(), source.reserved(), source.createdAt(), owner,
                token, leaseUntil, startedAt, status, actual, recovery);
    }

    private static BudgetReservation toLegacy(BudgetReservationLease lease) {
        return new BudgetReservation(lease.reservationId(), lease.executionId(), lease.reserved(),
                lease.status(), lease.createdAt(), lease.actualUsage());
    }

    private Account account(String id) {
        Account value = accounts.get(required(id, "executionId"));
        if (value == null) throw new IllegalArgumentException("budget not registered: " + id);
        return value;
    }

    private static void requireBeforeDeadline(Account account, Instant at) {
        Objects.requireNonNull(at, "at");
        if (!at.isBefore(account.budget.deadline())) throw new ResourceBudgetExceededException("execution deadline exceeded");
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
