package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.actions.api.ActionStatus;
import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ActionExecutionBatch;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ActionExecutionBatchStore;
import dev.supplychain.agent.runtime.cognitive.lifecycle.AuthorizedActionPlan;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Transactional batch header and append-only per-step receipt repository. */
public final class PostgresActionExecutionBatchStore implements ActionExecutionBatchStore {
    private final DataSource dataSource;
    private final String scopeId;
    public PostgresActionExecutionBatchStore(DataSource dataSource, String scopeId) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource"); this.scopeId = required(scopeId);
    }
    @Override public ActionExecutionBatch start(AuthorizedActionPlan plan, String batchId, String principalId, Instant at) {
        Objects.requireNonNull(plan, "plan"); Objects.requireNonNull(at, "at");
        String id = required(batchId), principal = required(principalId);
        if (!scopeId.equals(plan.planningRequest().workingMemory().scopeId().value())) throw new SecurityException("batch plan scope mismatch");
        return transaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO action_execution_batch(scope_id,batch_id,execution_id,principal_id,candidate_set_id,candidate_id,
                        plan_fingerprint,status,created_at,updated_at,revision)
                    VALUES(?,?,?,?,?,?,?,'STARTED',?,?,1) ON CONFLICT(scope_id,batch_id) DO NOTHING
                    """)) {
                insert.setString(1, scopeId); insert.setString(2, id); insert.setString(3, plan.executionId());
                insert.setString(4, principal); insert.setString(5, plan.candidateSetId()); insert.setString(6, plan.candidateId());
                insert.setString(7, plan.fingerprint()); insert.setObject(8, at); insert.setObject(9, at); insert.executeUpdate();
            }
            ActionExecutionBatch current = load(connection, id, true).orElseThrow();
            if (!current.executionId().equals(plan.executionId()) || !current.principalId().equals(principal)
                    || !current.candidateSetId().equals(plan.candidateSetId()) || !current.candidateId().equals(plan.candidateId())
                    || !current.planFingerprint().equals(plan.fingerprint())) throw new SecurityException("batch identity was reused for a different plan/principal");
            return current;
        });
    }
    @Override public ActionExecutionBatch append(String batchId, ActionExecutionBatch.ActionExecutionItem item,
                                                  ActionExecutionBatch.BatchStatus status, Instant at) {
        Objects.requireNonNull(item, "item"); Objects.requireNonNull(status, "status"); Objects.requireNonNull(at, "at");
        String id = required(batchId);
        return transaction(connection -> {
            ActionExecutionBatch current = load(connection, id, true).orElseThrow(() -> new IllegalStateException("unknown action batch: " + id));
            Optional<ActionExecutionBatch.ActionExecutionItem> existing = current.items().stream().filter(value -> value.stepIndex() == item.stepIndex()).findFirst();
            if (existing.isPresent()) {
                if (!existing.get().equals(item)) throw new SecurityException("action step receipt conflicts with committed receipt");
                return current;
            }
            if (current.status() == ActionExecutionBatch.BatchStatus.SUCCEEDED
                    || current.status() == ActionExecutionBatch.BatchStatus.REJECTED
                    || current.status() == ActionExecutionBatch.BatchStatus.FAILED) throw new IllegalStateException("terminal batch cannot accept steps");
            if (at.isBefore(current.updatedAt())) throw new IllegalArgumentException("batch timestamp moved backwards");
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO action_execution_step(scope_id,batch_id,step_index,action_id,status,operation_id,result_reference,
                        failure_code,failure_message,outcome_unknown,receipt_fingerprint,recorded_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                    """)) {
                insert.setString(1, scopeId); insert.setString(2, id); insert.setInt(3, item.stepIndex());
                insert.setString(4, item.actionId()); insert.setString(5, item.status().name());
                insert.setString(6, nullable(item.operationId())); insert.setString(7, nullable(item.resultReference()));
                insert.setString(8, nullable(item.failureCode())); insert.setString(9, nullable(item.failureMessage()));
                insert.setBoolean(10, item.outcomeUnknown()); insert.setString(11, PostgresJsonArtifactRepository.sha256(item.toString()));
                insert.setObject(12, at); insert.executeUpdate();
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE action_execution_batch SET status=?,updated_at=?,revision=revision+1
                    WHERE scope_id=? AND batch_id=? AND revision=?
                    """)) {
                update.setString(1, status.name()); update.setObject(2, at); update.setString(3, scopeId);
                update.setString(4, id); update.setLong(5, current.revision());
                if (update.executeUpdate() != 1) throw new IllegalStateException("action batch revision conflict");
            }
            return load(connection, id, false).orElseThrow();
        });
    }
    @Override public ActionExecutionBatch complete(String batchId, Instant at) {
        String id = required(batchId); Objects.requireNonNull(at, "at");
        return transaction(connection -> {
            ActionExecutionBatch current = load(connection, id, true).orElseThrow(() -> new IllegalStateException("unknown action batch: " + id));
            if (current.status() == ActionExecutionBatch.BatchStatus.SUCCEEDED) return current;
            if (current.status() != ActionExecutionBatch.BatchStatus.STARTED) throw new IllegalStateException("only a clean STARTED batch can complete");
            if (at.isBefore(current.updatedAt())) throw new IllegalArgumentException("batch timestamp moved backwards");
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE action_execution_batch SET status='SUCCEEDED',updated_at=?,revision=revision+1
                    WHERE scope_id=? AND batch_id=? AND revision=?
                    """)) {
                update.setObject(1, at); update.setString(2, scopeId); update.setString(3, id); update.setLong(4, current.revision());
                if (update.executeUpdate() != 1) throw new IllegalStateException("action batch revision conflict");
            }
            return load(connection, id, false).orElseThrow();
        });
    }
    @Override public Optional<ActionExecutionBatch> find(String batchId) {
        String id = required(batchId);
        try (Connection connection = dataSource.getConnection()) { return load(connection, id, false); }
        catch (SQLException ex) { throw new PostgresPersistenceException("PostgreSQL action batch read failed", ex); }
    }

    private Optional<ActionExecutionBatch> load(Connection connection, String id, boolean lock) throws SQLException {
        String query = "SELECT execution_id,principal_id,candidate_set_id,candidate_id,plan_fingerprint,status,created_at,updated_at,revision FROM action_execution_batch WHERE scope_id=? AND batch_id=?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, scopeId); statement.setString(2, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                String execution = row.getString(1), principal = row.getString(2), candidateSet = row.getString(3), candidate = row.getString(4), fingerprint = row.getString(5);
                var status = ActionExecutionBatch.BatchStatus.valueOf(row.getString(6));
                Instant created = row.getTimestamp(7).toInstant(), updated = row.getTimestamp(8).toInstant(); long revision = row.getLong(9);
                List<ActionExecutionBatch.ActionExecutionItem> items = loadItems(connection, id);
                return Optional.of(new ActionExecutionBatch(id, execution, principal, candidateSet, candidate, fingerprint,
                        status, items, created, updated, revision));
            }
        }
    }
    private List<ActionExecutionBatch.ActionExecutionItem> loadItems(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT step_index,action_id,status,operation_id,result_reference,failure_code,failure_message,outcome_unknown,receipt_fingerprint
                FROM action_execution_step WHERE scope_id=? AND batch_id=? ORDER BY step_index
                """)) {
            statement.setString(1, scopeId); statement.setString(2, id);
            try (ResultSet rows = statement.executeQuery()) {
                List<ActionExecutionBatch.ActionExecutionItem> items = new ArrayList<>();
                while (rows.next()) {
                    var item = new ActionExecutionBatch.ActionExecutionItem(rows.getInt(1), rows.getString(2),
                            ActionStatus.valueOf(rows.getString(3)), value(rows.getString(4)), value(rows.getString(5)),
                            value(rows.getString(6)), value(rows.getString(7)), rows.getBoolean(8));
                    if (!PostgresJsonArtifactRepository.sha256(item.toString()).equals(rows.getString(9).trim())) throw new SecurityException("action step receipt fingerprint mismatch");
                    items.add(item);
                }
                return items.stream().sorted(Comparator.comparingInt(ActionExecutionBatch.ActionExecutionItem::stepIndex)).toList();
            }
        }
    }
    @FunctionalInterface private interface SqlWork<T> { T run(Connection connection) throws SQLException; }
    private <T> T transaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try { T result = work.run(connection); connection.commit(); return result; }
            catch (SQLException | RuntimeException failure) { try { connection.rollback(); } catch (SQLException ex) { failure.addSuppressed(ex); } if (failure instanceof SQLException sql) throw sql; throw failure; }
        } catch (SQLException ex) { throw new PostgresPersistenceException("PostgreSQL action batch transaction failed", ex); }
    }
    private static String required(String v) { String s=Objects.requireNonNull(v,"identity").trim(); if(s.isEmpty())throw new IllegalArgumentException("identity cannot be blank"); return s; }
    private static String nullable(String v) { return v == null || v.isBlank() ? null : v; }
    private static String value(String v) { return v == null ? "" : v; }
}
