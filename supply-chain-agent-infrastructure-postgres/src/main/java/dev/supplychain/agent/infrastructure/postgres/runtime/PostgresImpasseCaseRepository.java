package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;
import dev.supplychain.agent.mechanisms.b2.ImpasseCaseRepository;
import dev.supplychain.agent.mechanisms.b2.ImpasseResolutionCase;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/** Durable B2 impasse lifecycle with optimistic version control and invariant-protected parent scope/objective. */
public final class PostgresImpasseCaseRepository implements ImpasseCaseRepository {
    private final DataSource dataSource;
    private final JsonCodec<ImpasseResolutionCase> codec;
    private final String scopeId;
    public PostgresImpasseCaseRepository(DataSource dataSource, JsonCodec<ImpasseResolutionCase> codec) {
        this(dataSource, null, codec);
    }
    public PostgresImpasseCaseRepository(DataSource dataSource, String scopeId, JsonCodec<ImpasseResolutionCase> codec) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource"); this.codec = Objects.requireNonNull(codec, "codec");
        this.scopeId = scopeId == null ? null : required(scopeId);
    }
    @Override public void saveNew(ImpasseResolutionCase value) {
        Objects.requireNonNull(value, "value");
        String payload = PostgresJsonArtifactRepository.validJson(codec.encode(value));
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO runtime_artifact(scope_id,artifact_kind,artifact_id,execution_id,owner_id,owner_version,
                    payload_fingerprint,payload,created_at,updated_at)
                VALUES(?,'B2_IMPASSE_CASE',?,?,?,?,?,CAST(? AS jsonb),?,?)
                """)) {
            statement.setString(1, value.scopeId().value()); statement.setString(2, value.id());
            statement.setString(3, value.executionId()); statement.setString(4, value.objectiveFingerprint());
            statement.setLong(5, value.version()); statement.setString(6, PostgresJsonArtifactRepository.sha256(payload));
            statement.setString(7, payload); statement.setObject(8, value.openedAt()); statement.setObject(9, value.openedAt());
            statement.executeUpdate();
        } catch (SQLException ex) {
            if ("23505".equals(ex.getSQLState())) throw new SecurityException("B2 case identity already exists", ex);
            throw new PostgresPersistenceException("PostgreSQL B2 case creation failed", ex);
        }
    }
    @Override public Optional<ImpasseResolutionCase> findById(String id) {
        String key = required(id);
        String sql = "SELECT payload::text,payload_fingerprint,execution_id,owner_id,owner_version FROM runtime_artifact "
                + "WHERE artifact_kind='B2_IMPASSE_CASE' AND artifact_id=?"
                + (scopeId == null ? " ORDER BY scope_id" : " AND scope_id=?");
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key); if (scopeId != null) statement.setString(2, scopeId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                String json = rows.getString(1); ImpasseResolutionCase value = codec.decode(json);
                verify(value, key, rows.getString(2), rows.getString(3), rows.getString(4), rows.getLong(5), json);
                if (rows.next()) throw new SecurityException("B2 case ID is ambiguous across scopes; use a scope-specific repository");
                return Optional.of(value);
            }
        } catch (SQLException ex) { throw new PostgresPersistenceException("PostgreSQL B2 case lookup failed", ex); }
    }
    @Override public boolean compareAndSet(String id, long expectedVersion, ImpasseResolutionCase replacement) {
        String key = required(id); Objects.requireNonNull(replacement, "replacement");
        if (scopeId != null && !scopeId.equals(replacement.scopeId().value())) throw new SecurityException("B2 case belongs to another persistence scope");
        if (!key.equals(replacement.id()) || replacement.version() != expectedVersion + 1) throw new IllegalArgumentException("invalid B2 case CAS replacement");
        String payload = PostgresJsonArtifactRepository.validJson(codec.encode(replacement));
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE runtime_artifact SET owner_version=?,payload_fingerprint=?,payload=CAST(? AS jsonb),updated_at=clock_timestamp()
                        WHERE scope_id=? AND artifact_kind='B2_IMPASSE_CASE' AND artifact_id=? AND owner_version=?
                          AND execution_id=? AND owner_id=?
                        """)) {
                    update.setLong(1, replacement.version()); update.setString(2, PostgresJsonArtifactRepository.sha256(payload));
                    update.setString(3, payload);
                    update.setString(4, replacement.scopeId().value()); update.setString(5, key); update.setLong(6, expectedVersion);
                    update.setString(7, replacement.executionId()); update.setString(8, replacement.objectiveFingerprint());
                    changed = update.executeUpdate();
                }
                if (changed == 0) { connection.rollback(); return false; }
                connection.commit(); return true;
            } catch (SQLException | RuntimeException failure) { try { connection.rollback(); } catch (SQLException ex) { failure.addSuppressed(ex); } throw failure; }
        } catch (SQLException ex) { throw new PostgresPersistenceException("PostgreSQL B2 case CAS failed", ex); }
    }
    private static void verify(ImpasseResolutionCase v, String id, String fp, String execution, String objective,
                               long memoryVersion, String payload) {
        if (!id.equals(v.id()) || !execution.equals(v.executionId()) || !objective.equals(v.objectiveFingerprint())
                || memoryVersion != v.version()
                || !fp.trim().equals(PostgresJsonArtifactRepository.sha256(payload))) {
            throw new SecurityException("B2 case payload/header mismatch");
        }
    }
    private static String required(String v) { String s=Objects.requireNonNull(v,"id").trim(); if(s.isEmpty())throw new IllegalArgumentException("id cannot be blank"); return s; }
}
