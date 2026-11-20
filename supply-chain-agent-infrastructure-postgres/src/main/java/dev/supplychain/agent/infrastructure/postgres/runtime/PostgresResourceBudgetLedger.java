package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;
import dev.supplychain.agent.mechanisms.b6.BudgetReservation;
import dev.supplychain.agent.mechanisms.b6.ReservationStatus;
import dev.supplychain.agent.mechanisms.b6.ResourceBudget;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetExceededException;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetLedger;
import dev.supplychain.agent.mechanisms.b6.ResourceBudgetSnapshot;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Scope-bound, transactional B6 resource-budget ledger. The execution account row is locked for
 * every mutation, so reservations made by independent application instances cannot oversubscribe
 * the same execution budget. Committed usage and reservation identities survive process restart.
 *
 * <p>This ledger governs computational/execution resources only; domain resources such as stock,
 * money and supplier capacity remain authoritative in their domain adapters.</p>
 */
public final class PostgresResourceBudgetLedger implements ResourceBudgetLedger {
    private final DataSource dataSource;
    private final String scopeId;

    public PostgresResourceBudgetLedger(DataSource dataSource, String scopeId) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.scopeId = required(scopeId, "scopeId");
    }

    @Override
    public void register(String executionId, ResourceBudget budget) {
        String execution = required(executionId, "executionId");
        Objects.requireNonNull(budget, "budget");
        transaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO b6_resource_budget(scope_id,execution_id,deadline,created_at,updated_at)
                    VALUES(?,?,?,clock_timestamp(),clock_timestamp()) ON CONFLICT(scope_id,execution_id) DO NOTHING
                    """)) {
                insert.setString(1, scopeId);
                insert.setString(2, execution);
                PostgresJdbc.setInstant(insert, 3, budget.deadline());
                if (insert.executeUpdate() != 1) {
                    throw new IllegalStateException("budget already registered for execution " + execution);
                }
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO b6_resource_budget_limit(scope_id,execution_id,unit,limit_value)
                    VALUES(?,?,?,?)
                    """)) {
                for (var entry : budget.limits().entrySet()) {
                    insert.setString(1, scopeId);
                    insert.setString(2, execution);
                    insert.setString(3, entry.getKey().name());
                    insert.setLong(4, entry.getValue());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            return null;
        });
    }

    @Override
    public BudgetReservation reserve(String executionId, String reservationId, ResourceUsage requested, Instant at) {
        String execution = required(executionId, "executionId");
        String reservation = required(reservationId, "reservationId");
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(at, "at");
        return transaction(connection -> {
            ResourceBudget budget = lockBudget(connection, execution);
            requireBeforeDeadline(budget, at);

            var existing = loadReservation(connection, execution, reservation, true);
            if (existing != null) {
                if (!existing.reserved().equals(requested)) {
                    throw new IllegalStateException("reservation ID reused with different amount");
                }
                if (existing.status() == ReservationStatus.RELEASED) {
                    throw new IllegalStateException("released reservation ID cannot be reused");
                }
                return existing;
            }

            Map<ResourceUnit, Long> consumed = readAmounts(connection,
                    "SELECT unit,consumed_value FROM b6_resource_budget_consumption WHERE scope_id=? AND execution_id=?",
                    execution);
            Map<ResourceUnit, Long> inFlight = readActiveReservations(connection, execution);
            for (ResourceUnit unit : ResourceUnit.values()) {
                long limit = budget.limit(unit);
                if (limit == Long.MAX_VALUE) continue;
                long current = consumed.getOrDefault(unit, 0L);
                if (current > limit) {
                    throw new ResourceBudgetExceededException("resource budget already exceeded for " + unit);
                }
                long available = limit - current;
                long held = inFlight.getOrDefault(unit, 0L);
                if (held > available) {
                    throw new ResourceBudgetExceededException("reserved work already exhausts budget for " + unit);
                }
                available -= held;
                if (requested.amount(unit) > available) {
                    throw new ResourceBudgetExceededException("resource budget exceeded for " + unit);
                }
            }

            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO b6_resource_budget_reservation(scope_id,execution_id,reservation_id,status,created_at,updated_at)
                    VALUES(?,?,?,'ACTIVE',?,?)
                    """)) {
                insert.setString(1, scopeId);
                insert.setString(2, execution);
                insert.setString(3, reservation);
                PostgresJdbc.setInstant(insert, 4, at);
                PostgresJdbc.setInstant(insert, 5, at);
                insert.executeUpdate();
            }
            insertAmounts(connection, "b6_resource_budget_reservation_item", "reserved_value",
                    execution, reservation, requested.amounts());
            touchAccount(connection, execution, at);
            return new BudgetReservation(reservation, execution, requested, ReservationStatus.ACTIVE, at, null);
        });
    }

    @Override
    public ResourceBudgetSnapshot commit(String executionId, String reservationId, ResourceUsage actual, Instant at) {
        String execution = required(executionId, "executionId");
        String reservation = required(reservationId, "reservationId");
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(at, "at");
        return transaction(connection -> {
            ResourceBudget budget = lockBudget(connection, execution);
            BudgetReservation current = loadReservation(connection, execution, reservation, true);
            if (current == null) throw new IllegalStateException("reservation not found: " + reservation);
            if (current.status() == ReservationStatus.COMMITTED) {
                if (!current.actualUsage().equals(actual)) {
                    throw new IllegalStateException("committed reservation cannot be charged with different actual usage");
                }
                return snapshotLocked(connection, execution, budget, at);
            }
            if (current.status() != ReservationStatus.ACTIVE) {
                throw new IllegalStateException("released reservation cannot be committed");
            }

            Map<ResourceUnit, Long> consumed = readAmounts(connection,
                    "SELECT unit,consumed_value FROM b6_resource_budget_consumption WHERE scope_id=? AND execution_id=?",
                    execution);
            // Record actual consumption even when it exceeds the reservation or budget. Losing an
            // overrun would allow subsequent work to reuse resources that have already been spent.
            try (PreparedStatement upsert = connection.prepareStatement("""
                    INSERT INTO b6_resource_budget_consumption(scope_id,execution_id,unit,consumed_value)
                    VALUES(?,?,?,?) ON CONFLICT(scope_id,execution_id,unit)
                    DO UPDATE SET consumed_value=EXCLUDED.consumed_value
                    """)) {
                for (ResourceUnit unit : ResourceUnit.values()) {
                    upsert.setString(1, scopeId);
                    upsert.setString(2, execution);
                    upsert.setString(3, unit.name());
                    upsert.setLong(4, saturatedAdd(consumed.getOrDefault(unit, 0L), actual.amount(unit)));
                    upsert.addBatch();
                }
                upsert.executeBatch();
            }
            insertAmounts(connection, "b6_resource_budget_actual_item", "actual_value",
                    execution, reservation, actual.amounts());
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE b6_resource_budget_reservation SET status='COMMITTED',updated_at=GREATEST(updated_at,?)
                    WHERE scope_id=? AND execution_id=? AND reservation_id=? AND status='ACTIVE'
                    """)) {
                PostgresJdbc.setInstant(update, 1, at);
                update.setString(2, scopeId);
                update.setString(3, execution);
                update.setString(4, reservation);
                if (update.executeUpdate() != 1) throw new IllegalStateException("budget reservation changed concurrently");
            }
            touchAccount(connection, execution, at);
            return snapshotLocked(connection, execution, budget, at);
        });
    }

    @Override
    public ResourceBudgetSnapshot release(String executionId, String reservationId, Instant at) {
        String execution = required(executionId, "executionId");
        String reservation = required(reservationId, "reservationId");
        Objects.requireNonNull(at, "at");
        return transaction(connection -> {
            ResourceBudget budget = lockBudget(connection, execution);
            BudgetReservation current = loadReservation(connection, execution, reservation, true);
            if (current == null) throw new IllegalStateException("reservation not found: " + reservation);
            if (current.status() == ReservationStatus.RELEASED) {
                return snapshotLocked(connection, execution, budget, at);
            }
            if (current.status() != ReservationStatus.ACTIVE) {
                throw new IllegalStateException("committed reservation cannot be released");
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE b6_resource_budget_reservation SET status='RELEASED',updated_at=GREATEST(updated_at,?)
                    WHERE scope_id=? AND execution_id=? AND reservation_id=? AND status='ACTIVE'
                    """)) {
                PostgresJdbc.setInstant(update, 1, at);
                update.setString(2, scopeId);
                update.setString(3, execution);
                update.setString(4, reservation);
                if (update.executeUpdate() != 1) throw new IllegalStateException("budget reservation changed concurrently");
            }
            touchAccount(connection, execution, at);
            return snapshotLocked(connection, execution, budget, at);
        });
    }

    @Override
    public ResourceBudgetSnapshot snapshot(String executionId, Instant at) {
        String execution = required(executionId, "executionId");
        Objects.requireNonNull(at, "at");
        return transaction(connection -> {
            ResourceBudget budget = lockBudget(connection, execution);
            return snapshotLocked(connection, execution, budget, at);
        });
    }

    private ResourceBudgetSnapshot snapshotLocked(Connection connection, String execution,
                                                   ResourceBudget budget, Instant at) throws SQLException {
        Map<ResourceUnit, Long> consumed = readAmounts(connection,
                "SELECT unit,consumed_value FROM b6_resource_budget_consumption WHERE scope_id=? AND execution_id=?",
                execution);
        Map<ResourceUnit, Long> reserved = readActiveReservations(connection, execution);
        return new ResourceBudgetSnapshot(execution, budget, consumed, reserved, at);
    }

    private ResourceBudget lockBudget(Connection connection, String execution) throws SQLException {
        Instant deadline;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT deadline FROM b6_resource_budget WHERE scope_id=? AND execution_id=? FOR UPDATE
                """)) {
            statement.setString(1, scopeId);
            statement.setString(2, execution);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) throw new IllegalArgumentException("budget not registered: " + execution);
                deadline = row.getTimestamp(1).toInstant();
            }
        }
        EnumMap<ResourceUnit, Long> limits = new EnumMap<>(ResourceUnit.class);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT unit,limit_value FROM b6_resource_budget_limit WHERE scope_id=? AND execution_id=?
                """)) {
            statement.setString(1, scopeId);
            statement.setString(2, execution);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) limits.put(ResourceUnit.valueOf(rows.getString(1)), rows.getLong(2));
            }
        }
        return new ResourceBudget(limits, deadline);
    }

    private BudgetReservation loadReservation(Connection connection, String execution,
                                               String reservation, boolean lock) throws SQLException {
        String sql = "SELECT status,created_at FROM b6_resource_budget_reservation "
                + "WHERE scope_id=? AND execution_id=? AND reservation_id=?" + (lock ? " FOR UPDATE" : "");
        ReservationStatus status;
        Instant createdAt;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, scopeId);
            statement.setString(2, execution);
            statement.setString(3, reservation);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                status = ReservationStatus.valueOf(row.getString(1));
                createdAt = row.getTimestamp(2).toInstant();
            }
        }
        Map<ResourceUnit, Long> reserved = readReservationAmounts(connection,
                "b6_resource_budget_reservation_item", "reserved_value", execution, reservation);
        ResourceUsage actual = status == ReservationStatus.COMMITTED
                ? new ResourceUsage(readReservationAmounts(connection,
                        "b6_resource_budget_actual_item", "actual_value", execution, reservation)) : null;
        return new BudgetReservation(reservation, execution, new ResourceUsage(reserved), status, createdAt, actual);
    }

    private Map<ResourceUnit, Long> readActiveReservations(Connection connection, String execution) throws SQLException {
        boolean anyActive;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS(SELECT 1 FROM b6_resource_budget_reservation
                    WHERE scope_id=? AND execution_id=? AND status='ACTIVE')
                """)) {
            statement.setString(1, scopeId);
            statement.setString(2, execution);
            try (ResultSet row = statement.executeQuery()) { row.next(); anyActive = row.getBoolean(1); }
        }
        if (!anyActive) return Map.of();
        EnumMap<ResourceUnit, Long> totals = new EnumMap<>(ResourceUnit.class);
        for (ResourceUnit unit : ResourceUnit.values()) totals.put(unit, 0L);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT item.unit,item.reserved_value
                FROM b6_resource_budget_reservation_item item
                JOIN b6_resource_budget_reservation reservation
                  ON reservation.scope_id=item.scope_id AND reservation.execution_id=item.execution_id
                 AND reservation.reservation_id=item.reservation_id
                WHERE item.scope_id=? AND item.execution_id=? AND reservation.status='ACTIVE'
                ORDER BY item.unit,item.reservation_id
                """)) {
            statement.setString(1, scopeId);
            statement.setString(2, execution);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ResourceUnit unit = ResourceUnit.valueOf(rows.getString(1));
                    totals.put(unit, saturatedAdd(totals.get(unit), rows.getLong(2)));
                }
            }
        }
        return totals;
    }

    private Map<ResourceUnit, Long> readReservationAmounts(Connection connection, String table,
                                                           String column, String execution,
                                                           String reservation) throws SQLException {
        // Table/column names are internal constants at call sites, never caller-controlled input.
        EnumMap<ResourceUnit, Long> result = new EnumMap<>(ResourceUnit.class);
        String sql = "SELECT unit," + column + " FROM " + table + " WHERE scope_id=? AND execution_id=? AND reservation_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, scopeId);
            statement.setString(2, execution);
            statement.setString(3, reservation);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.put(ResourceUnit.valueOf(rows.getString(1)), rows.getLong(2));
            }
        }
        return result;
    }

    private Map<ResourceUnit, Long> readAmounts(Connection connection, String sql,
                                                String execution) throws SQLException {
        EnumMap<ResourceUnit, Long> result = new EnumMap<>(ResourceUnit.class);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, scopeId);
            statement.setString(2, execution);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.put(ResourceUnit.valueOf(rows.getString(1)), rows.getLong(2));
            }
        }
        return result;
    }

    private void insertAmounts(Connection connection, String table, String column, String execution,
                               String reservation, Map<ResourceUnit, Long> amounts) throws SQLException {
        // Table/column names are fixed by this class and not exposed to external callers.
        String sql = "INSERT INTO " + table + "(scope_id,execution_id,reservation_id,unit," + column + ") VALUES(?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (var entry : amounts.entrySet()) {
                statement.setString(1, scopeId);
                statement.setString(2, execution);
                statement.setString(3, reservation);
                statement.setString(4, entry.getKey().name());
                statement.setLong(5, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void touchAccount(Connection connection, String execution, Instant at) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE b6_resource_budget SET updated_at=GREATEST(updated_at,?) WHERE scope_id=? AND execution_id=?
                """)) {
            PostgresJdbc.setInstant(statement, 1, at);
            statement.setString(2, scopeId);
            statement.setString(3, execution);
            if (statement.executeUpdate() != 1) throw new IllegalStateException("budget account disappeared during update");
        }
    }

    private static void requireBeforeDeadline(ResourceBudget budget, Instant at) {
        if (!at.isBefore(budget.deadline())) throw new ResourceBudgetExceededException("execution deadline exceeded");
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    @FunctionalInterface private interface SqlWork<T> { T run(Connection connection) throws SQLException; }

    private <T> T transaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                if (failure instanceof SQLException sqlFailure) throw sqlFailure;
                throw failure;
            }
        } catch (SQLException failure) {
            throw new PostgresPersistenceException("PostgreSQL B6 resource-budget transaction failed", failure);
        }
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
