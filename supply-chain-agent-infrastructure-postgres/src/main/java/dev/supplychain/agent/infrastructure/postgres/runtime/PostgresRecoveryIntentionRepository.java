package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.mechanisms.b4.RecoveryIntention;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionId;
import dev.supplychain.agent.mechanisms.b4.RecoveryIntentionRepository;
import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/** B4 intention repository with immutable objective fingerprint, version CAS and append-only history. */
public final class PostgresRecoveryIntentionRepository implements RecoveryIntentionRepository {
    private final DataSource dataSource;
    private final JsonCodec<RecoveryIntention> codec;
    private final String scopeId;
    public PostgresRecoveryIntentionRepository(DataSource dataSource, JsonCodec<RecoveryIntention> codec) {
        this(dataSource, null, codec);
    }
    public PostgresRecoveryIntentionRepository(DataSource dataSource, String scopeId, JsonCodec<RecoveryIntention> codec) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.scopeId = scopeId == null ? null : required(scopeId);
    }

    @Override public void saveNew(RecoveryIntention intention) {
        Objects.requireNonNull(intention, "intention");
        String scope = intention.scopeId().value();
        if (scopeId != null && !scopeId.equals(scope)) throw new SecurityException("recovery intention belongs to another persistence scope");
        String payload = PostgresJsonArtifactRepository.validJson(codec.encode(intention));
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO recovery_intention(scope_id,intention_id,objective_fingerprint,version,status,payload,created_at,updated_at)
                        VALUES(?,?,?,?,?,CAST(? AS jsonb),?,?)
                        """)) {
                    insert.setString(1, scope); insert.setString(2, intention.id().value());
                    insert.setString(3, intention.objectiveFingerprint()); insert.setLong(4, intention.version());
                    insert.setString(5, intention.status().name()); insert.setString(6, payload);
                    PostgresJdbc.setInstant(insert, 7, intention.createdAt()); PostgresJdbc.setInstant(insert, 8, intention.updatedAt());
                    insert.executeUpdate();
                }
                appendHistory(connection, intention, payload);
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure); throw failure;
            }
        } catch (SQLException ex) {
            if ("23505".equals(ex.getSQLState())) throw new SecurityException("recovery intention identity already exists", ex);
            throw new PostgresPersistenceException("PostgreSQL B4 intention creation failed", ex);
        }
    }

    @Override public Optional<RecoveryIntention> findById(RecoveryIntentionId id) {
        Objects.requireNonNull(id, "id");
        String sql = "SELECT payload::text,objective_fingerprint,version,status FROM recovery_intention WHERE intention_id=?"
                + (scopeId == null ? " ORDER BY scope_id" : " AND scope_id=?");
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.value()); if (scopeId != null) statement.setString(2, scopeId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                RecoveryIntention value = codec.decode(rows.getString(1));
                assertHeader(value, rows.getString(2), rows.getLong(3), rows.getString(4), id);
                if (rows.next()) throw new SecurityException("recovery intention ID is ambiguous across scopes; use a scope-specific repository");
                return Optional.of(value);
            }
        } catch (SQLException ex) { throw new PostgresPersistenceException("PostgreSQL B4 intention lookup failed", ex); }
    }

    @Override public boolean compareAndSet(RecoveryIntentionId id, long expectedVersion, RecoveryIntention replacement) {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(replacement, "replacement");
        if (!id.equals(replacement.id())) throw new SecurityException("replacement intention ID changed");
        if (scopeId != null && !scopeId.equals(replacement.scopeId().value())) throw new SecurityException("replacement intention belongs to another persistence scope");
        if (replacement.version() != expectedVersion + 1) throw new IllegalArgumentException("replacement intention version must increment by one");
        String payload = PostgresJsonArtifactRepository.validJson(codec.encode(replacement));
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE recovery_intention SET version=?,status=?,payload=CAST(? AS jsonb),updated_at=?
                        WHERE scope_id=? AND intention_id=? AND version=? AND objective_fingerprint=?
                        """)) {
                    update.setLong(1, replacement.version()); update.setString(2, replacement.status().name());
                    update.setString(3, payload); PostgresJdbc.setInstant(update, 4, replacement.updatedAt());
                    update.setString(5, replacement.scopeId().value()); update.setString(6, id.value());
                    update.setLong(7, expectedVersion); update.setString(8, replacement.objectiveFingerprint());
                    changed = update.executeUpdate();
                }
                if (changed == 0) { connection.rollback(); return false; }
                appendHistory(connection, replacement, payload);
                connection.commit(); return true;
            } catch (SQLException | RuntimeException failure) { rollback(connection, failure); throw failure; }
        } catch (SQLException ex) { throw new PostgresPersistenceException("PostgreSQL B4 intention CAS failed", ex); }
    }

    private static void appendHistory(Connection connection, RecoveryIntention value, String payload) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO recovery_intention_history(scope_id,intention_id,version,objective_fingerprint,status,payload,recorded_at)
                VALUES(?,?,?,?,?,CAST(? AS jsonb),?)
                """)) {
            statement.setString(1, value.scopeId().value()); statement.setString(2, value.id().value());
            statement.setLong(3, value.version()); statement.setString(4, value.objectiveFingerprint());
            statement.setString(5, value.status().name()); statement.setString(6, payload); PostgresJdbc.setInstant(statement, 7, value.updatedAt());
            statement.executeUpdate();
        }
    }
    private static void assertHeader(RecoveryIntention value, String fingerprint, long version, String status, RecoveryIntentionId id) {
        if (!id.equals(value.id()) || !fingerprint.trim().equals(value.objectiveFingerprint())
                || version != value.version() || !status.equals(value.status().name())) {
            throw new SecurityException("recovery intention payload/header consistency check failed");
        }
    }
    private static String required(String value) { String result=Objects.requireNonNull(value,"scopeId").trim(); if(result.isEmpty())throw new IllegalArgumentException("scopeId cannot be blank"); return result; }
    private static void rollback(Connection c, Throwable failure) { try { c.rollback(); } catch (SQLException ex) { failure.addSuppressed(ex); } }
}
