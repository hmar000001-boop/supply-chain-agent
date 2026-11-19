package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/** Aggregate operational counters only; no credentials, principals or business payloads are exposed. */
public final class PostgresRuntimeMetrics {
    private static final String QUERY = """
            SELECT
              (SELECT COUNT(*) FROM action_operation_ledger WHERE scope_id=? AND state='UNKNOWN_OUTCOME') AS unknown_operations,
              (SELECT COUNT(*) FROM b2_child_step_receipt WHERE scope_id=? AND state='UNKNOWN_OUTCOME') AS unknown_child_steps,
              (SELECT COUNT(*) FROM action_execution_batch WHERE scope_id=? AND status='UNKNOWN_OUTCOME') AS unknown_batches,
              (SELECT COUNT(*) FROM runtime_approval WHERE scope_id=? AND status='PENDING' AND expires_at>clock_timestamp()) AS pending_approvals,
              (SELECT COUNT(*) FROM outbox_message WHERE scope_id=? AND state='PENDING') AS pending_outbox,
              (SELECT COUNT(*) FROM outbox_message WHERE scope_id=? AND state='IN_FLIGHT') AS in_flight_outbox,
              (SELECT COUNT(*) FROM outbox_message WHERE scope_id=? AND state='DEAD_LETTER') AS dead_outbox,
              (SELECT COUNT(*) FROM inbox_message WHERE scope_id=? AND state='PROCESSING') AS processing_inbox,
              (SELECT COUNT(*) FROM inbox_message WHERE scope_id=? AND state='DEAD_LETTER') AS dead_inbox,
              COALESCE((SELECT GREATEST(0, EXTRACT(EPOCH FROM (clock_timestamp()-MIN(created_at))))
                        FROM outbox_message WHERE scope_id=? AND state='PENDING'),0) AS oldest_pending_outbox_seconds,
              COALESCE((SELECT GREATEST(0, EXTRACT(EPOCH FROM (clock_timestamp()-MIN(updated_at))))
                        FROM action_operation_ledger WHERE scope_id=? AND state='UNKNOWN_OUTCOME'),0) AS oldest_unknown_operation_seconds
            """;

    private final DataSource dataSource;
    private final String scopeId;

    public PostgresRuntimeMetrics(DataSource dataSource, String scopeId) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.scopeId = required(scopeId);
    }

    public Snapshot read() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(QUERY)) {
            for (int index = 1; index <= 11; index++) statement.setString(index, scopeId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) throw new SQLException("runtime metrics query returned no row");
                return new Snapshot(row.getLong("unknown_operations"), row.getLong("unknown_child_steps"),
                        row.getLong("unknown_batches"), row.getLong("pending_approvals"),
                        row.getLong("pending_outbox"), row.getLong("in_flight_outbox"),
                        row.getLong("dead_outbox"), row.getLong("processing_inbox"),
                        row.getLong("dead_inbox"), row.getDouble("oldest_pending_outbox_seconds"),
                        row.getDouble("oldest_unknown_operation_seconds"));
            }
        } catch (SQLException ex) {
            throw new PostgresPersistenceException("PostgreSQL runtime metrics query failed", ex);
        }
    }

    private static String required(String value) {
        String result = Objects.requireNonNull(value, "scopeId").trim();
        if (result.isEmpty()) throw new IllegalArgumentException("scopeId cannot be blank");
        return result;
    }

    public record Snapshot(long unknownOperations, long unknownChildSteps, long unknownBatches,
                           long pendingApprovals, long pendingOutbox, long inFlightOutbox,
                           long deadOutbox, long processingInbox, long deadInbox,
                           double oldestPendingOutboxSeconds, double oldestUnknownOperationSeconds) { }
}
