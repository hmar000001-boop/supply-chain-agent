package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/** Scope-bound immutable JSONB artifact store shared by the M5/M6/B2 artifact adapters. */
public final class PostgresJsonArtifactRepository<T> {
    private final DataSource dataSource;
    private final String scopeId;
    private final String artifactKind;
    private final JsonCodec<T> codec;

    public PostgresJsonArtifactRepository(DataSource dataSource, String scopeId,
                                          String artifactKind, JsonCodec<T> codec) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.scopeId = required(scopeId, "scopeId");
        this.artifactKind = required(artifactKind, "artifactKind");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public void save(String artifactId, String executionId, String ownerId, long ownerVersion,
                     T artifact, Instant at) {
        String id = required(artifactId, "artifactId");
        String execution = optional(executionId);
        String owner = optional(ownerId);
        if (ownerVersion < 0) throw new IllegalArgumentException("ownerVersion cannot be negative");
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(at, "at");
        String payload = validJson(codec.encode(artifact));
        String fingerprint = sha256(payload);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO runtime_artifact(scope_id, artifact_kind, artifact_id, execution_id, owner_id,
                            owner_version, payload_fingerprint, payload, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                        ON CONFLICT (scope_id, artifact_kind, artifact_id) DO NOTHING
                        """)) {
                    insert.setString(1, scopeId); insert.setString(2, artifactKind); insert.setString(3, id);
                    insert.setString(4, execution); insert.setString(5, owner); insert.setLong(6, ownerVersion);
                    insert.setString(7, fingerprint); insert.setString(8, payload);
                    PostgresJdbc.setInstant(insert, 9, at); PostgresJdbc.setInstant(insert, 10, at); insert.executeUpdate();
                }
                try (PreparedStatement select = connection.prepareStatement("""
                        SELECT execution_id, owner_id, owner_version, payload_fingerprint
                        FROM runtime_artifact WHERE scope_id=? AND artifact_kind=? AND artifact_id=? FOR UPDATE
                        """)) {
                    select.setString(1, scopeId); select.setString(2, artifactKind); select.setString(3, id);
                    try (ResultSet row = select.executeQuery()) {
                        if (!row.next()) {
                            throw new SecurityException("immutable artifact row disappeared after insert: kind="
                                    + artifactKind + ", id=" + id);
                        }
                        java.util.List<String> mismatches = new java.util.ArrayList<>();
                        if (!Objects.equals(execution, row.getString(1))) mismatches.add("execution_id");
                        if (!Objects.equals(owner, row.getString(2))) mismatches.add("owner_id");
                        if (ownerVersion != row.getLong(3)) mismatches.add("owner_version");
                        if (!fingerprint.equals(row.getString(4).trim())) mismatches.add("payload_fingerprint");
                        if (!mismatches.isEmpty()) {
                            throw new SecurityException("immutable artifact identity conflict: kind=" + artifactKind
                                    + ", id=" + id + ", mismatched_fields=" + String.join(",", mismatches));
                        }
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            }
        } catch (SQLException ex) {
            throw new PostgresPersistenceException("PostgreSQL immutable runtime artifact write failed", ex);
        }
    }

    public Optional<T> find(String artifactId) {
        String id = required(artifactId, "artifactId");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT payload::text,payload_fingerprint FROM runtime_artifact
                     WHERE scope_id=? AND artifact_kind=? AND artifact_id=?
                       AND (expires_at IS NULL OR expires_at > clock_timestamp())
                     """)) {
            statement.setString(1, scopeId); statement.setString(2, artifactKind); statement.setString(3, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                T value = codec.decode(rows.getString(1));
                String canonical = validJson(codec.encode(value));
                if (!sha256(canonical).equals(rows.getString(2).trim())) {
                    throw new SecurityException("immutable runtime artifact payload fingerprint mismatch");
                }
                return Optional.of(value);
            }
        } catch (SQLException ex) {
            throw new PostgresPersistenceException("PostgreSQL immutable runtime artifact read failed", ex);
        }
    }

    public String scopeId() { return scopeId; }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public static String validJson(String json) {
        String value = Objects.requireNonNull(json, "codec JSON").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("codec produced empty JSON");
        return value;
    }

    private static void rollback(Connection connection, Throwable original) {
        try { connection.rollback(); } catch (SQLException rollback) { original.addSuppressed(rollback); }
    }
    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
