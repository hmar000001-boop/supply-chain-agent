package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.actions.api.*;
import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL M3 idempotency ledger. Lease expiry is converted to UNKNOWN_OUTCOME, never a redispatch.
 * Production dispatch uses fencing tokens so stale workers cannot write over newer acquisitions.
 */
public final class PostgresActionExecutionLedger implements FencedActionExecutionLedger {
    private final DataSource dataSource;
    private final String scopeId;
    private final JsonCodec<ActionReceipt> receiptCodec;
    private final JsonCodec<ActionFailure> failureCodec;
    private final Duration leaseDuration;

    public PostgresActionExecutionLedger(DataSource dataSource, String scopeId,
                                         JsonCodec<ActionReceipt> receiptCodec,
                                         JsonCodec<ActionFailure> failureCodec, Duration leaseDuration) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.scopeId = required(scopeId);
        this.receiptCodec = Objects.requireNonNull(receiptCodec, "receiptCodec");
        this.failureCodec = Objects.requireNonNull(failureCodec, "failureCodec");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (leaseDuration.isZero() || leaseDuration.isNegative()) throw new IllegalArgumentException("leaseDuration must be positive");
    }

    @Override public Optional<ActionLedgerClaim> inspect(IdempotencyLedgerKey key, String fingerprint) {
        Objects.requireNonNull(key, "key");
        String request = fingerprint(fingerprint);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT request_fingerprint,operation_id,state,retryable,outcome_unknown,
                           receipt_payload::text,failure_payload::text,
                           (state='IN_PROGRESS' AND lease_until <= clock_timestamp()) AS lease_expired
                    FROM action_operation_ledger
                    WHERE scope_id=? AND principal_id=? AND action_id=? AND idempotency_key=?
                    FOR UPDATE
                    """)) {
                statement.setString(1, scopeId);
                bindKey(statement, 2, key);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) {
                        boolean legacy = legacyRecordExists(connection, key);
                        connection.commit();
                        if (legacy) return Optional.of(unknown(null,
                                "A pre-scope idempotency record exists; reconcile its external outcome before using this key in a scoped runtime"));
                        return Optional.empty();
                    }
                    if (!request.equals(row.getString(1).trim())) {
                        connection.commit();
                        return Optional.of(keyReused());
                    }
                    String operationId = row.getString(2);
                    String state = row.getString(3);
                    if ("IN_PROGRESS".equals(state) && row.getBoolean(8)) {
                        ActionFailure failure = unknownFailure(
                                "Operation lease expired; external outcome must be reconciled before any retry");
                        try (PreparedStatement update = connection.prepareStatement("""
                                UPDATE action_operation_ledger
                                SET state='UNKNOWN_OUTCOME', retryable=false, outcome_unknown=true,
                                    failure_payload=CAST(? AS jsonb), lease_owner=NULL, lease_until=NULL,
                                    updated_at=clock_timestamp()
                                WHERE scope_id=? AND principal_id=? AND action_id=? AND idempotency_key=?
                                  AND state='IN_PROGRESS' AND lease_until <= clock_timestamp()
                                """)) {
                            update.setString(1, PostgresJsonArtifactRepository.validJson(failureCodec.encode(failure)));
                            update.setString(2, scopeId);
                            bindKey(update, 3, key);
                            if (update.executeUpdate() != 1) {
                                throw new IllegalStateException("expired M3 lease changed while being marked unknown");
                            }
                        }
                        connection.commit();
                        return Optional.of(new ActionLedgerClaim(ActionLedgerClaim.Status.UNKNOWN_OUTCOME,
                                operationId, null, failure));
                    }
                    boolean retryable = row.getBoolean(4);
                    boolean outcomeUnknown = row.getBoolean(5);
                    // The ActionExecutionLedger contract treats a definitively failed, safely
                    // retryable operation as absent during inspection. ActionDispatcher will then
                    // run preconditions and claim a new fenced attempt. UNKNOWN_OUTCOME is never
                    // converted to absence and therefore cannot be blindly redispatched.
                    if ("FAILED".equals(state) && retryable && !outcomeUnknown) {
                        connection.commit();
                        return Optional.empty();
                    }
                    ActionLedgerClaim claim = mapClaim(operationId, state, retryable, outcomeUnknown,
                            row.getString(6), row.getString(7));
                    connection.commit();
                    return Optional.of(claim);
                }
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        } catch (SQLException ex) {
            throw new PostgresPersistenceException("PostgreSQL M3 ledger inspection failed", ex);
        }
    }

    @Override public FencedClaim claimFenced(IdempotencyLedgerKey key, String fingerprint, Instant startedAt) {
        Objects.requireNonNull(key, "key"); String request = fingerprint(fingerprint); Objects.requireNonNull(startedAt, "startedAt");
        String owner = UUID.randomUUID().toString();
        return transaction(connection -> {
            if (legacyRecordExists(connection, key)) {
                return new FencedClaim(unknown(null,
                        "A pre-scope idempotency record exists; reconcile its external outcome before using this key in a scoped runtime"), 0);
            }
            String proposedOperation = UUID.randomUUID().toString();
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO action_operation_ledger(scope_id,principal_id,action_id,idempotency_key,request_fingerprint,operation_id,
                        state,retryable,outcome_unknown,lease_owner,lease_until,fencing_token,started_at,updated_at)
                    VALUES(?,?,?,?,?,?, 'IN_PROGRESS',false,false,?,clock_timestamp() + (? * interval '1 millisecond'),1,?,?)
                    ON CONFLICT(scope_id,principal_id,action_id,idempotency_key) DO NOTHING
                    """)) {
                insert.setString(1, scopeId); insert.setString(2, key.principalId());
                insert.setString(3, key.actionId().value()); insert.setString(4, key.key());
                insert.setString(5, request); insert.setString(6, proposedOperation); insert.setString(7, owner);
                insert.setLong(8, leaseDuration.toMillis()); PostgresJdbc.setInstant(insert, 9, startedAt); PostgresJdbc.setInstant(insert, 10, startedAt);
                int inserted = insert.executeUpdate();
                if (inserted == 1) {
                    return new FencedClaim(new ActionLedgerClaim(ActionLedgerClaim.Status.ACQUIRED, proposedOperation, null, null), 1L);
                }
            }
            LedgerRow row = load(connection, key, true).orElseThrow(() -> new IllegalStateException("ledger claim insert/select lost its row"));
            if (!row.fingerprint().equals(request)) return new FencedClaim(keyReused(), 0);
            return switch (row.state()) {
                case "IN_PROGRESS" -> {
                    if (row.leaseExpired()) {
                        ActionFailure failure = unknownFailure("Operation lease expired; external outcome is unknown and must be reconciled");
                        persistFailure(connection, key, row.fencingToken(), failure, "UNKNOWN_OUTCOME", startedAt);
                        yield new FencedClaim(new ActionLedgerClaim(ActionLedgerClaim.Status.UNKNOWN_OUTCOME,
                                row.operationId(), null, failure), 0);
                    }
                    yield new FencedClaim(new ActionLedgerClaim(ActionLedgerClaim.Status.IN_PROGRESS, row.operationId(), null,
                            new ActionFailure(ActionFailureCode.OPERATION_IN_PROGRESS, "An operation with this idempotency key is already running", false, false)), 0);
                }
                case "COMPLETED" -> new FencedClaim(new ActionLedgerClaim(ActionLedgerClaim.Status.COMPLETED,
                        row.operationId(), receiptCodec.decode(row.receiptJson()), null), 0);
                case "UNKNOWN_OUTCOME" -> new FencedClaim(new ActionLedgerClaim(ActionLedgerClaim.Status.UNKNOWN_OUTCOME,
                        row.operationId(), null, failureCodec.decode(row.failureJson())), 0);
                case "FAILED" -> {
                    ActionFailure failure = failureCodec.decode(row.failureJson());
                    if (!failure.retryable() || failure.outcomeUnknown()) {
                        yield new FencedClaim(new ActionLedgerClaim(ActionLedgerClaim.Status.FAILED,row.operationId(),null,failure),0);
                    }
                    long nextFence = row.fencingToken() + 1;
                    try (PreparedStatement retry = connection.prepareStatement("""
                            UPDATE action_operation_ledger SET state='IN_PROGRESS',retryable=false,outcome_unknown=false,
                                lease_owner=?,lease_until=clock_timestamp() + (? * interval '1 millisecond'),fencing_token=?,updated_at=?,failure_payload=NULL
                            WHERE scope_id=? AND principal_id=? AND action_id=? AND idempotency_key=? AND state='FAILED' AND fencing_token=?
                            """)) {
                        retry.setString(1, owner); retry.setLong(2, leaseDuration.toMillis()); retry.setLong(3, nextFence);
                        PostgresJdbc.setInstant(retry, 4, startedAt); retry.setString(5, scopeId); bindKey(retry, 6, key); retry.setLong(9, row.fencingToken());
                        if (retry.executeUpdate() != 1) throw new IllegalStateException("M3 retry acquisition lost a fencing race");
                    }
                    yield new FencedClaim(new ActionLedgerClaim(ActionLedgerClaim.Status.ACQUIRED,row.operationId(),null,null),nextFence);
                }
                default -> throw new IllegalStateException("unknown M3 ledger state: " + row.state());
            };
        });
    }

    @Override public void completeFenced(IdempotencyLedgerKey key, long fencingToken, ActionReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        verifyReceiptBinding(key, receipt);
        if (fencingToken < 1) throw new SecurityException("missing M3 fencing token");
        String payload = PostgresJsonArtifactRepository.validJson(receiptCodec.encode(receipt));
        boolean committed = transaction(connection -> {
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE action_operation_ledger SET state='COMPLETED',retryable=false,outcome_unknown=false,
                        receipt_payload=CAST(? AS jsonb),failure_payload=NULL,lease_owner=NULL,lease_until=NULL,updated_at=clock_timestamp()
                    WHERE scope_id=? AND principal_id=? AND action_id=? AND idempotency_key=? AND state='IN_PROGRESS'
                      AND fencing_token=? AND operation_id=? AND lease_until > clock_timestamp()
                    """)) {
                update.setString(1, payload); update.setString(2, scopeId); bindKey(update, 3, key);
                update.setLong(6, fencingToken); update.setString(7, receipt.operationId());
                if (update.executeUpdate() != 1) {
                    markExpiredUnknown(connection, key, receipt.completedAt());
                    return false;
                }
            }
            return true;
        });
        if (!committed) throw new SecurityException("M3 completion rejected: lease expired or fencing token is stale");
    }

    @Override public void failFenced(IdempotencyLedgerKey key, long fencingToken, ActionFailure failure) {
        Objects.requireNonNull(failure, "failure");
        if (fencingToken < 1) throw new SecurityException("missing M3 fencing token");
        String payload = PostgresJsonArtifactRepository.validJson(failureCodec.encode(failure));
        boolean recorded = transaction(connection -> {
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE action_operation_ledger SET state=?,retryable=?,outcome_unknown=?,failure_payload=CAST(? AS jsonb),
                        receipt_payload=NULL,lease_owner=NULL,lease_until=NULL,updated_at=clock_timestamp()
                    WHERE scope_id=? AND principal_id=? AND action_id=? AND idempotency_key=? AND state='IN_PROGRESS'
                      AND fencing_token=? AND lease_until > clock_timestamp()
                    """)) {
                update.setString(1, failure.outcomeUnknown() ? "UNKNOWN_OUTCOME" : "FAILED");
                update.setBoolean(2, failure.retryable()); update.setBoolean(3, failure.outcomeUnknown()); update.setString(4, payload);
                update.setString(5, scopeId); bindKey(update, 6, key); update.setLong(9, fencingToken);
                if (update.executeUpdate() != 1) {
                    markExpiredUnknown(connection, key, Instant.EPOCH);
                    return false;
                }
            }
            return true;
        });
        if (!recorded) throw new SecurityException("M3 failure rejected: lease expired or fencing token is stale");
    }

    /** Resolve only after the external adapter has authoritatively confirmed that the operation committed. */
    public void reconcileUnknownAsCompleted(IdempotencyLedgerKey key, ActionReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        verifyReceiptBinding(key, receipt);
        String payload = PostgresJsonArtifactRepository.validJson(receiptCodec.encode(receipt));
        updateUnknown(key, """
                UPDATE action_operation_ledger SET state='COMPLETED',receipt_payload=CAST(? AS jsonb),failure_payload=NULL,
                    retryable=false,outcome_unknown=false,lease_owner=NULL,lease_until=NULL,updated_at=clock_timestamp()
                WHERE scope_id=? AND principal_id=? AND action_id=? AND idempotency_key=? AND state='UNKNOWN_OUTCOME' AND operation_id=?
                """, statement -> {
            statement.setString(1, payload); statement.setString(2, scopeId); bindKey(statement, 3, key);
            statement.setString(6, receipt.operationId());
        });
    }

    /** Resolve only after external reconciliation confirms no effect was applied; retry remains a separate claim. */
    public void reconcileUnknownAsNotApplied(IdempotencyLedgerKey key, ActionFailure evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (!evidence.retryable() || evidence.outcomeUnknown()) throw new IllegalArgumentException("not-applied reconciliation must provide a safe retryable failure");
        String payload = PostgresJsonArtifactRepository.validJson(failureCodec.encode(evidence));
        updateUnknown(key, """
                UPDATE action_operation_ledger SET state='FAILED',failure_payload=CAST(? AS jsonb),receipt_payload=NULL,
                    retryable=true,outcome_unknown=false,lease_owner=NULL,lease_until=NULL,updated_at=clock_timestamp()
                WHERE scope_id=? AND principal_id=? AND action_id=? AND idempotency_key=? AND state='UNKNOWN_OUTCOME'
                """, statement -> { statement.setString(1, payload); statement.setString(2, scopeId); bindKey(statement, 3, key); });
    }

    /** Explicit lease extension for handlers whose bounded execution can exceed the configured lease. */
    public void renewLease(IdempotencyLedgerKey key, long fencingToken, Duration extension) {
        if (fencingToken < 1 || extension == null || extension.isNegative() || extension.isZero()) throw new IllegalArgumentException("invalid lease renewal");
        transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE action_operation_ledger SET lease_until=clock_timestamp() + (? * interval '1 millisecond'), updated_at=clock_timestamp()
                    WHERE scope_id=? AND principal_id=? AND action_id=? AND idempotency_key=? AND state='IN_PROGRESS'
                      AND fencing_token=? AND lease_until > clock_timestamp()
                    """)) {
                statement.setLong(1, extension.toMillis()); statement.setString(2, scopeId);
                bindKey(statement, 3, key); statement.setLong(6, fencingToken);
                if (statement.executeUpdate() != 1) throw new SecurityException("cannot renew expired or superseded M3 lease");
            }
            return null;
        });
    }

    private void updateUnknown(IdempotencyLedgerKey key, String sql, Binder binder) {
        transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                if (statement.executeUpdate() != 1) throw new IllegalStateException("M3 reconciliation requires a matching UNKNOWN_OUTCOME record");
            }
            return null;
        });
    }

    private void markExpiredUnknown(Connection connection, IdempotencyLedgerKey key, Instant at) throws SQLException {
        ActionFailure failure = unknownFailure("M3 operation lease expired before its owner committed a receipt; reconcile external state");
        String payload = PostgresJsonArtifactRepository.validJson(failureCodec.encode(failure));
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE action_operation_ledger SET state='UNKNOWN_OUTCOME',retryable=false,outcome_unknown=true,
                    failure_payload=CAST(? AS jsonb),lease_owner=NULL,lease_until=NULL,updated_at=clock_timestamp()
                WHERE scope_id=? AND principal_id=? AND action_id=? AND idempotency_key=? AND state='IN_PROGRESS' AND lease_until <= clock_timestamp()
                """)) {
            update.setString(1,payload); update.setString(2, scopeId); bindKey(update,3,key); update.executeUpdate();
        }
    }

    private void persistFailure(Connection connection, IdempotencyLedgerKey key, long token,
                                ActionFailure failure, String state, Instant at) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE action_operation_ledger SET state=?,retryable=?,outcome_unknown=?,failure_payload=CAST(? AS jsonb),
                    lease_owner=NULL,lease_until=NULL,updated_at=clock_timestamp()
                WHERE scope_id=? AND principal_id=? AND action_id=? AND idempotency_key=? AND state='IN_PROGRESS' AND fencing_token=?
                """)) {
            update.setString(1,state); update.setBoolean(2,failure.retryable()); update.setBoolean(3,failure.outcomeUnknown());
            update.setString(4,PostgresJsonArtifactRepository.validJson(failureCodec.encode(failure)));
            update.setString(5, scopeId); bindKey(update,6,key); update.setLong(9,token); update.executeUpdate();
        }
    }

    /** Fail closed on pre-V008 rows whose original tenant cannot be inferred safely. */
    private static boolean legacyRecordExists(Connection connection, IdempotencyLedgerKey key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM action_operation_ledger
                WHERE scope_id='__legacy_unscoped__' AND principal_id=? AND action_id=? AND idempotency_key=?
                """)) {
            bindKey(statement, key);
            try (ResultSet row = statement.executeQuery()) { return row.next(); }
        }
    }

    private Optional<LedgerRow> load(Connection connection, IdempotencyLedgerKey key, boolean lock) throws SQLException {
        String sql = "SELECT request_fingerprint,operation_id,state,retryable,outcome_unknown,lease_owner,lease_until,fencing_token,receipt_payload::text,failure_payload::text,(state='IN_PROGRESS' AND lease_until <= clock_timestamp()) FROM action_operation_ledger WHERE scope_id=? AND principal_id=? AND action_id=? AND idempotency_key=?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, scopeId); bindKey(statement,2,key);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Optional.empty();
                var lease = row.getTimestamp(7);
                return Optional.of(new LedgerRow(row.getString(1).trim(),row.getString(2),row.getString(3),row.getBoolean(4),
                        row.getBoolean(5),row.getString(6),lease == null ? null : lease.toInstant(),row.getLong(8),row.getString(9),row.getString(10),row.getBoolean(11)));
            }
        }
    }
    private ActionLedgerClaim mapClaim(String op, String state, boolean retryable, boolean unknown, String receipt, String failure) {
        return switch (state) {
            case "IN_PROGRESS" -> new ActionLedgerClaim(ActionLedgerClaim.Status.IN_PROGRESS,op,null,
                    new ActionFailure(ActionFailureCode.OPERATION_IN_PROGRESS,"An operation with this idempotency key is already running",false,false));
            case "COMPLETED" -> new ActionLedgerClaim(ActionLedgerClaim.Status.COMPLETED,op,receiptCodec.decode(receipt),null);
            case "FAILED" -> new ActionLedgerClaim(ActionLedgerClaim.Status.FAILED,op,null,failureCodec.decode(failure));
            case "UNKNOWN_OUTCOME" -> new ActionLedgerClaim(ActionLedgerClaim.Status.UNKNOWN_OUTCOME,op,null,failureCodec.decode(failure));
            default -> throw new IllegalStateException("unknown M3 state: " + state);
        };
    }
    private static ActionLedgerClaim unknown(String op, String message) {
        return new ActionLedgerClaim(ActionLedgerClaim.Status.UNKNOWN_OUTCOME,op,null,unknownFailure(message));
    }
    private static ActionLedgerClaim keyReused() {
        return new ActionLedgerClaim(ActionLedgerClaim.Status.KEY_REUSED,null,null,
                new ActionFailure(ActionFailureCode.IDEMPOTENCY_KEY_REUSED,"Idempotency key was previously used for a different action input",false,false));
    }
    private static ActionFailure unknownFailure(String message) { return new ActionFailure(ActionFailureCode.UNKNOWN_OUTCOME,message,false,true); }
    private static void bindKey(PreparedStatement statement, IdempotencyLedgerKey key) throws SQLException { bindKey(statement,1,key); }
    private static void bindKey(PreparedStatement statement, int start, IdempotencyLedgerKey key) throws SQLException {
        statement.setString(start,key.principalId()); statement.setString(start+1,key.actionId().value()); statement.setString(start+2,key.key());
    }
    @FunctionalInterface private interface Binder { void bind(PreparedStatement statement) throws SQLException; }
    @FunctionalInterface private interface SqlWork<T> { T run(Connection connection) throws SQLException; }
    private <T> T transaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try { T value=work.run(connection); connection.commit(); return value; }
            catch (SQLException | RuntimeException failure) { try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); } if (failure instanceof SQLException sql) throw sql; throw failure; }
        } catch (SQLException ex) { throw new PostgresPersistenceException("PostgreSQL M3 ledger transaction failed",ex); }
    }
    private static String fingerprint(String value) {
        String result = required(value);
        if (!result.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("request fingerprint must be a lowercase SHA-256 hex digest");
        }
        return result;
    }
    private static void verifyReceiptBinding(IdempotencyLedgerKey key, ActionReceipt receipt) {
        Objects.requireNonNull(key, "key");
        if (!key.actionId().equals(receipt.actionId()) || !key.principalId().equals(receipt.principalId())) {
            throw new SecurityException("M3 receipt principal/action does not match the idempotency key");
        }
    }
    private static String required(String value) { String result=Objects.requireNonNull(value,"value").trim(); if(result.isEmpty())throw new IllegalArgumentException("value cannot be blank"); return result; }
    private record LedgerRow(String fingerprint,String operationId,String state,boolean retryable,boolean outcomeUnknown,
                             String leaseOwner,Instant leaseUntil,long fencingToken,String receiptJson,String failureJson,
                             boolean leaseExpired) { }
}
