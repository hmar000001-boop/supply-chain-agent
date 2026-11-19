package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Scope-bound transactional outbox and deduplicating inbox. Delivery is at-least-once;
 * consumers must be idempotent. Message identity and publisher/consumer claims are scoped.
 */
public final class PostgresMessagingStore {
    private final DataSource dataSource;
    private final String scopeId;

    public PostgresMessagingStore(DataSource dataSource, String scopeId) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.scopeId = required(scopeId);
    }

    public void enqueueOutbox(Connection transaction, String messageId, String aggregateId, String messageType,
                              String payloadJson, Instant createdAt) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(createdAt, "createdAt");
        String payload = PostgresJsonArtifactRepository.validJson(payloadJson);
        try (PreparedStatement statement = transaction.prepareStatement("""
                INSERT INTO outbox_message(scope_id,message_id,aggregate_id,message_type,payload,state,attempts,
                    next_attempt_at,created_at)
                VALUES(?,?,?,?,CAST(? AS jsonb),'PENDING',0,?,?)
                """)) {
            statement.setString(1, scopeId);
            statement.setString(2, required(messageId));
            statement.setString(3, required(aggregateId));
            statement.setString(4, required(messageType));
            statement.setString(5, payload);
            PostgresJdbc.setInstant(statement, 6, createdAt);
            PostgresJdbc.setInstant(statement, 7, createdAt);
            statement.executeUpdate();
        } catch (SQLException ex) {
            if ("23505".equals(ex.getSQLState())) throw new SecurityException("outbox message ID already exists in this scope", ex);
            throw new PostgresPersistenceException("outbox enqueue failed", ex);
        }
    }

    public void enqueueOutbox(String messageId, String aggregateId, String messageType,
                              String payloadJson, Instant createdAt) {
        transaction(connection -> {
            enqueueOutbox(connection, messageId, aggregateId, messageType, payloadJson, createdAt);
            return null;
        });
    }

    public List<OutboxMessage> claimOutbox(String workerId, Instant now, Duration lease, int limit) {
        String worker = required(workerId);
        String leaseOwner = worker + ":" + UUID.randomUUID();
        Objects.requireNonNull(now, "now");
        positive(lease, "lease");
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("outbox claim limit must be in [1,500]");
        return transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    WITH picked AS (
                        SELECT scope_id,message_id FROM outbox_message
                        WHERE scope_id=? AND ((state='PENDING' AND next_attempt_at<=clock_timestamp())
                           OR (state='IN_FLIGHT' AND lease_until<=clock_timestamp()))
                        ORDER BY next_attempt_at,created_at,message_id
                        LIMIT ? FOR UPDATE SKIP LOCKED
                    )
                    UPDATE outbox_message message SET state='IN_FLIGHT',attempts=message.attempts+1,
                        lease_owner=?,lease_until=clock_timestamp() + (? * interval '1 millisecond')
                    FROM picked WHERE message.scope_id=picked.scope_id AND message.message_id=picked.message_id
                    RETURNING message.message_id,message.aggregate_id,message.message_type,message.payload::text,
                        message.attempts,message.created_at,message.lease_until
                    """)) {
                statement.setString(1, scopeId);
                statement.setInt(2, limit);
                statement.setString(3, leaseOwner);
                statement.setLong(4, lease.toMillis());
                try (ResultSet rows = statement.executeQuery()) {
                    List<OutboxMessage> result = new ArrayList<>();
                    while (rows.next()) result.add(new OutboxMessage(rows.getString(1), rows.getString(2),
                            rows.getString(3), rows.getString(4), rows.getInt(5), rows.getTimestamp(6).toInstant(),
                            leaseOwner, rows.getTimestamp(7).toInstant()));
                    return List.copyOf(result);
                }
            }
        });
    }

    public void acknowledgeOutbox(String messageId, String workerId, Instant at) {
        Objects.requireNonNull(at, "at");
        transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE outbox_message SET state='PUBLISHED',published_at=clock_timestamp(),
                        lease_owner=NULL,lease_until=NULL,last_error=NULL
                    WHERE scope_id=? AND message_id=? AND state='IN_FLIGHT' AND lease_owner=?
                      AND lease_until>clock_timestamp()
                    """)) {
                statement.setString(1, scopeId);
                statement.setString(2, required(messageId));
                statement.setString(3, required(workerId));
                if (statement.executeUpdate() != 1) {
                    throw new SecurityException("outbox acknowledgement rejected: lease is expired or owned by another publisher");
                }
            }
            return null;
        });
    }

    public void failOutbox(String messageId, String workerId, Instant at, String error, int maxAttempts,
                           Duration initialBackoff, Duration maximumBackoff) {
        Objects.requireNonNull(at, "at");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        positive(initialBackoff, "initialBackoff");
        positive(maximumBackoff, "maximumBackoff");
        transaction(connection -> {
            int attempts;
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT attempts FROM outbox_message
                    WHERE scope_id=? AND message_id=? AND state='IN_FLIGHT' AND lease_owner=?
                      AND lease_until>clock_timestamp() FOR UPDATE
                    """)) {
                query.setString(1, scopeId);
                query.setString(2, required(messageId));
                query.setString(3, required(workerId));
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next()) throw new SecurityException("outbox failure rejected: message is not leased by this publisher");
                    attempts = rows.getInt(1);
                }
            }
            boolean dead = attempts >= maxAttempts;
            Duration delay = dead ? Duration.ZERO : backoff(initialBackoff, maximumBackoff, attempts);
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE outbox_message SET state=?,next_attempt_at=clock_timestamp() + (? * interval '1 millisecond'),
                        lease_owner=NULL,lease_until=NULL,last_error=?
                    WHERE scope_id=? AND message_id=? AND state='IN_FLIGHT' AND lease_owner=?
                      AND lease_until>clock_timestamp()
                    """)) {
                update.setString(1, dead ? "DEAD_LETTER" : "PENDING");
                update.setLong(2, delay.toMillis());
                update.setString(3, truncate(error));
                update.setString(4, scopeId);
                update.setString(5, required(messageId));
                update.setString(6, required(workerId));
                if (update.executeUpdate() != 1) throw new IllegalStateException("outbox lease changed during failure recording");
            }
            return null;
        });
    }

    public InboxClaim beginInbox(String consumerId, String messageId, String payloadJson, String workerId,
                                 Instant now, Duration lease) {
        String consumer = required(consumerId);
        String id = required(messageId);
        String worker = required(workerId);
        String leaseOwner = worker + ":" + UUID.randomUUID();
        String payload = PostgresJsonArtifactRepository.validJson(payloadJson);
        positive(lease, "lease");
        Objects.requireNonNull(now, "now");
        return transaction(connection -> {
            // Hash PostgreSQL's jsonb representation, not producer whitespace/property ordering.
            String canonicalPayload = canonicalJson(connection, payload);
            String fingerprint = PostgresJsonArtifactRepository.sha256(canonicalPayload);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO inbox_message(scope_id,consumer_id,message_id,payload_fingerprint,payload,state,attempts,
                        received_at,next_attempt_at,updated_at)
                    VALUES(?,?,?,?,CAST(? AS jsonb),'RECEIVED',0,?,?,?)
                    ON CONFLICT(scope_id,consumer_id,message_id) DO NOTHING
                    """)) {
                insert.setString(1, scopeId);
                insert.setString(2, consumer);
                insert.setString(3, id);
                insert.setString(4, fingerprint);
                insert.setString(5, canonicalPayload);
                PostgresJdbc.setInstant(insert, 6, now);
                PostgresJdbc.setInstant(insert, 7, now);
                PostgresJdbc.setInstant(insert, 8, now);
                insert.executeUpdate();
            }
            String state;
            String storedFingerprint;
            String storedPayload;
            int attempts;
            boolean leaseLive;
            boolean retryWait;
            try (PreparedStatement select = connection.prepareStatement("""
                    SELECT payload_fingerprint,payload::text,state,attempts,lease_until,next_attempt_at,
                           (lease_until > clock_timestamp()) AS lease_live,
                           (next_attempt_at > clock_timestamp()) AS retry_wait
                    FROM inbox_message WHERE scope_id=? AND consumer_id=? AND message_id=? FOR UPDATE
                    """)) {
                select.setString(1, scopeId);
                select.setString(2, consumer);
                select.setString(3, id);
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) throw new IllegalStateException("inbox insert/select lost its row");
                    storedFingerprint = rows.getString(1).trim();
                    storedPayload = rows.getString(2);
                    state = rows.getString(3);
                    attempts = rows.getInt(4);
                    leaseLive = rows.getBoolean(7);
                    retryWait = rows.getBoolean(8);
                }
            }
            if (!storedFingerprint.equals(fingerprint)) {
                return new InboxClaim(InboxStatus.PAYLOAD_CONFLICT, id, storedPayload, attempts,
                        "message ID was reused with a different payload");
            }
            if ("PROCESSED".equals(state)) return new InboxClaim(InboxStatus.ALREADY_PROCESSED, id, storedPayload, attempts, "");
            if ("DEAD_LETTER".equals(state)) return new InboxClaim(InboxStatus.DEAD_LETTER, id, storedPayload, attempts, "message is quarantined");
            if ("RECEIVED".equals(state) && retryWait) return new InboxClaim(InboxStatus.IN_PROGRESS, id, storedPayload, attempts, "message retry backoff has not elapsed");
            if ("PROCESSING".equals(state) && leaseLive) return new InboxClaim(InboxStatus.IN_PROGRESS, id, storedPayload, attempts, "another worker owns the inbox lease");
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE inbox_message SET state='PROCESSING',attempts=attempts+1,claimed_by=?,
                        lease_until=clock_timestamp() + (? * interval '1 millisecond'),updated_at=clock_timestamp()
                    WHERE scope_id=? AND consumer_id=? AND message_id=? AND state IN ('RECEIVED','PROCESSING')
                    """)) {
                update.setString(1, leaseOwner);
                update.setLong(2, lease.toMillis());
                update.setString(3, scopeId);
                update.setString(4, consumer);
                update.setString(5, id);
                if (update.executeUpdate() != 1) throw new IllegalStateException("inbox claim lost a lock-protected transition");
            }
            return new InboxClaim(InboxStatus.ACQUIRED, id, storedPayload, attempts + 1, leaseOwner);
        });
    }

    public void acknowledgeInbox(String consumerId, String messageId, String workerId, Instant at) {
        Objects.requireNonNull(at, "at");
        transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE inbox_message SET state='PROCESSED',claimed_by=NULL,lease_until=NULL,last_error=NULL,
                        updated_at=clock_timestamp()
                    WHERE scope_id=? AND consumer_id=? AND message_id=? AND state='PROCESSING'
                      AND claimed_by=? AND lease_until>clock_timestamp()
                    """)) {
                statement.setString(1, scopeId);
                statement.setString(2, required(consumerId));
                statement.setString(3, required(messageId));
                statement.setString(4, required(workerId));
                if (statement.executeUpdate() != 1) throw new SecurityException("inbox acknowledgement rejected: expired or foreign lease");
            }
            return null;
        });
    }

    public void failInbox(String consumerId, String messageId, String workerId, Instant at, String error,
                          int maxAttempts, Duration initialBackoff, Duration maximumBackoff) {
        Objects.requireNonNull(at, "at");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        positive(initialBackoff, "initialBackoff");
        positive(maximumBackoff, "maximumBackoff");
        transaction(connection -> {
            int attempts;
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT attempts FROM inbox_message WHERE scope_id=? AND consumer_id=? AND message_id=?
                      AND state='PROCESSING' AND claimed_by=? AND lease_until>clock_timestamp() FOR UPDATE
                    """)) {
                query.setString(1, scopeId);
                query.setString(2, required(consumerId));
                query.setString(3, required(messageId));
                query.setString(4, required(workerId));
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next()) throw new SecurityException("inbox failure rejected: message is not leased by this worker");
                    attempts = rows.getInt(1);
                }
            }
            boolean dead = attempts >= maxAttempts;
            Duration delay = dead ? Duration.ZERO : backoff(initialBackoff, maximumBackoff, attempts);
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE inbox_message SET state=?,claimed_by=NULL,lease_until=NULL,last_error=?,
                        next_attempt_at=clock_timestamp() + (? * interval '1 millisecond'),updated_at=clock_timestamp()
                    WHERE scope_id=? AND consumer_id=? AND message_id=? AND state='PROCESSING'
                      AND claimed_by=? AND lease_until>clock_timestamp()
                    """)) {
                update.setString(1, dead ? "DEAD_LETTER" : "RECEIVED");
                update.setString(2, truncate(error));
                update.setLong(3, delay.toMillis());
                update.setString(4, scopeId);
                update.setString(5, required(consumerId));
                update.setString(6, required(messageId));
                update.setString(7, required(workerId));
                if (update.executeUpdate() != 1) throw new IllegalStateException("inbox lease changed during failure recording");
            }
            return null;
        });
    }

    public <T> T inTransaction(TransactionWork<T> work) { return transaction(connection -> work.run(connection)); }

    @FunctionalInterface public interface TransactionWork<T> { T run(Connection connection) throws SQLException; }
    public record OutboxMessage(String messageId, String aggregateId, String messageType, String payloadJson, int attempt,
                                 Instant createdAt, String leaseOwner, Instant leaseUntil) { }
    public enum InboxStatus { ACQUIRED, ALREADY_PROCESSED, IN_PROGRESS, PAYLOAD_CONFLICT, DEAD_LETTER }
    /** For ACQUIRED, workerOrReason is a one-claim lease token; pass it to acknowledgeInbox/failInbox. */
    public record InboxClaim(InboxStatus status, String messageId, String payloadJson, int attempts, String workerOrReason) { }

    private interface Work<T> { T run(Connection connection) throws SQLException; }

    private <T> T transaction(Work<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                if (failure instanceof SQLException sql) throw sql;
                throw failure;
            }
        } catch (SQLException ex) {
            throw new PostgresPersistenceException("PostgreSQL inbox/outbox transaction failed", ex);
        }
    }

    private static String canonicalJson(Connection connection, String json) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT CAST(? AS jsonb)::text")) {
            statement.setString(1, json);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) throw new SQLException("PostgreSQL did not return canonical JSONB text");
                return row.getString(1);
            }
        }
    }

    private static Duration backoff(Duration initial, Duration maximum, int attempt) {
        long multiplier = 1L << Math.min(30, Math.max(0, attempt - 1));
        long millis;
        try { millis = Math.multiplyExact(initial.toMillis(), multiplier); }
        catch (ArithmeticException ex) { millis = Long.MAX_VALUE; }
        return Duration.ofMillis(Math.min(millis, maximum.toMillis()));
    }
    private static void positive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) throw new IllegalArgumentException(name + " must be positive");
    }
    private static String required(String value) {
        String result = Objects.requireNonNull(value, "value").trim();
        if (result.isEmpty()) throw new IllegalArgumentException("value cannot be blank");
        return result;
    }
    private static String truncate(String value) {
        if (value == null) return "unspecified failure";
        String result = value.trim();
        return result.length() > 2048 ? result.substring(0, 2048) : result;
    }
}
