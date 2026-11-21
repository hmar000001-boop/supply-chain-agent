package dev.supplychain.agent.infrastructure.postgres.memory;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureArtifact;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureArtifactCodec;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureArtifactStore;
import dev.supplychain.agent.memory.procedural.ProcedureArtifactReference;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/** PostgreSQL immutable content-addressed store for typed, approved B2 recovery artifacts. */
public final class PostgresRecoveryProcedureArtifactStore implements RecoveryProcedureArtifactStore {
    private final DataSource dataSource;
    private final RecoveryProcedureArtifactCodec codec;

    public PostgresRecoveryProcedureArtifactStore(DataSource dataSource) {
        this(dataSource, new RecoveryProcedureArtifactCodec());
    }

    public PostgresRecoveryProcedureArtifactStore(DataSource dataSource, RecoveryProcedureArtifactCodec codec) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override public ProcedureArtifactReference publish(RecoveryProcedureArtifact artifact) {
        byte[] payload = codec.encode(Objects.requireNonNull(artifact, "artifact"));
        String digest = sha256(payload);
        String location = "urn:sha256:" + digest;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO coala_recovery_procedure_artifact(artifact_sha256, artifact_location, payload)
                    VALUES (?, ?, ?) ON CONFLICT (artifact_sha256) DO NOTHING
                    """)) {
                insert.setString(1, digest);
                insert.setString(2, location);
                insert.setBytes(3, payload);
                insert.executeUpdate();
            }
            try (PreparedStatement select = connection.prepareStatement("SELECT artifact_location, payload "
                    + "FROM coala_recovery_procedure_artifact WHERE artifact_sha256 = ?")) {
                select.setString(1, digest);
                try (ResultSet rows = select.executeQuery()) {
                    if (!rows.next()) throw new SQLException("content-addressed artifact insert was not visible");
                    if (!location.equals(rows.getString(1)) || !Arrays.equals(payload, rows.getBytes(2))) {
                        throw new SecurityException("existing recovery artifact does not match its content address");
                    }
                }
            }
            return new ProcedureArtifactReference(URI.create(location), digest);
        } catch (SQLException ex) {
            throw new PostgresPersistenceException("could not publish immutable B2 recovery artifact", ex);
        }
    }

    @Override public Optional<RecoveryProcedureArtifact> retrieve(ProcedureArtifactReference reference, MemoryScopeId expectedScope) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(expectedScope, "expectedScope");
        if (!reference.location().toString().equals("urn:sha256:" + reference.sha256())) {
            throw new SecurityException("recovery artifact URI and digest do not match");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement select = connection.prepareStatement("SELECT artifact_location, payload "
                     + "FROM coala_recovery_procedure_artifact WHERE artifact_sha256 = ?")) {
            select.setString(1, reference.sha256());
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                String location = rows.getString(1);
                byte[] payload = rows.getBytes(2);
                if (!reference.location().toString().equals(location) || !sha256(payload).equals(reference.sha256())) {
                    throw new SecurityException("stored recovery artifact failed URI or digest verification");
                }
                RecoveryProcedureArtifact artifact = codec.decode(payload);
                if (!artifact.scopeId().equals(expectedScope)) {
                    throw new SecurityException("recovery artifact belongs to another memory scope");
                }
                return Optional.of(artifact);
            }
        } catch (SQLException ex) {
            throw new PostgresPersistenceException("could not retrieve immutable B2 recovery artifact", ex);
        }
    }

    private static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
}
