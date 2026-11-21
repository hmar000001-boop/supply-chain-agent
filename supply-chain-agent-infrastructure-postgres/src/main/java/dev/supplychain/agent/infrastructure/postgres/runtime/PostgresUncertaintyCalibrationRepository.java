package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;
import dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationProfile;
import dev.supplychain.agent.mechanisms.b6.UncertaintyCalibrationRepository;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Scope-bound PostgreSQL CAS repository for immutable B6 calibration profile revisions. */
public final class PostgresUncertaintyCalibrationRepository implements UncertaintyCalibrationRepository {
    private final DataSource dataSource;
    private final String scopeId;
    private final JsonCodec<UncertaintyCalibrationProfile> codec;

    public PostgresUncertaintyCalibrationRepository(DataSource dataSource, String scopeId,
            JsonCodec<UncertaintyCalibrationProfile> codec) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.scopeId = required(scopeId, "scopeId");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override public void saveNew(UncertaintyCalibrationProfile profile) {
        requireScope(profile.scopeId());
        String payload = PostgresJsonArtifactRepository.validJson(codec.encode(profile));
        try (Connection c = dataSource.getConnection(); PreparedStatement q = c.prepareStatement("""
                INSERT INTO b6_uncertainty_calibration_profile(scope_id,profile_id,target_id,profile_version,status,
                    training_dataset_fingerprint,validation_dataset_fingerprint,training_sample_count,validation_sample_count,
                    validation_ece,payload,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?)
                """)) {
            bind(q, profile, payload, 1);
            if (q.executeUpdate() != 1) throw new PostgresPersistenceException("B6 calibration profile insert affected no row");
        } catch (SQLException e) { throw new PostgresPersistenceException("PostgreSQL B6 calibration profile insert failed", e); }
    }

    @Override public Optional<UncertaintyCalibrationProfile> findById(String id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement q = c.prepareStatement(
                "SELECT payload::text FROM b6_uncertainty_calibration_profile WHERE scope_id=? AND profile_id=?")) {
            q.setString(1, scopeId); q.setString(2, required(id, "id"));
            try (ResultSet r = q.executeQuery()) { return r.next() ? Optional.of(codec.decode(r.getString(1))) : Optional.empty(); }
        } catch (SQLException e) { throw new PostgresPersistenceException("PostgreSQL B6 calibration profile lookup failed", e); }
    }

    @Override public Optional<UncertaintyCalibrationProfile> findActive(MemoryScopeId scope, String targetId) {
        requireScope(scope);
        try (Connection c = dataSource.getConnection(); PreparedStatement q = c.prepareStatement("""
                SELECT payload::text FROM b6_uncertainty_calibration_profile
                WHERE scope_id=? AND target_id=? AND status='ACTIVE'
                ORDER BY profile_version DESC LIMIT 1
                """)) {
            q.setString(1, scopeId); q.setString(2, required(targetId, "targetId"));
            try (ResultSet r = q.executeQuery()) { return r.next() ? Optional.of(codec.decode(r.getString(1))) : Optional.empty(); }
        } catch (SQLException e) { throw new PostgresPersistenceException("PostgreSQL B6 active calibration lookup failed", e); }
    }

    @Override public boolean compareAndSet(String id, long expectedVersion, UncertaintyCalibrationProfile replacement) {
        requireScope(replacement.scopeId());
        if (!required(id, "id").equals(replacement.id())) throw new SecurityException("B6 calibration CAS cannot change profile identity");
        String payload = PostgresJsonArtifactRepository.validJson(codec.encode(replacement));
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement q = c.prepareStatement("""
                    UPDATE b6_uncertainty_calibration_profile SET target_id=?,profile_version=?,status=?,
                        training_dataset_fingerprint=?,validation_dataset_fingerprint=?,training_sample_count=?,
                        validation_sample_count=?,validation_ece=?,payload=CAST(? AS jsonb),updated_at=?
                    WHERE scope_id=? AND profile_id=? AND profile_version=?
                    """)) {
                q.setString(1, replacement.targetId()); q.setLong(2, replacement.version());
                q.setString(3, replacement.status().name()); q.setString(4, replacement.trainingDatasetFingerprint());
                q.setString(5, replacement.validationDatasetFingerprint().isBlank() ? null : replacement.validationDatasetFingerprint());
                q.setLong(6, replacement.trainingSampleCount()); q.setLong(7, replacement.validationSampleCount());
                q.setDouble(8, replacement.validationExpectedCalibrationError()); q.setString(9, payload);
                PostgresJdbc.setInstant(q, 10, replacement.activatedAt() != null ? replacement.activatedAt()
                        : replacement.validatedAt() != null ? replacement.validatedAt() : replacement.createdAt());
                q.setString(11, scopeId); q.setString(12, id); q.setLong(13, expectedVersion);
                int updated = q.executeUpdate(); c.commit(); return updated == 1;
            } catch (SQLException | RuntimeException failure) {
                try { c.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        } catch (SQLException e) { throw new PostgresPersistenceException("PostgreSQL B6 calibration profile CAS failed", e); }
    }

    @Override public boolean activateReplacingActive(String id, long expectedVersion,
            UncertaintyCalibrationProfile replacement) {
        requireScope(replacement.scopeId());
        if (!required(id, "id").equals(replacement.id())
                || replacement.version() != expectedVersion + 1
                || replacement.status() != UncertaintyCalibrationProfile.Status.ACTIVE) {
            throw new IllegalArgumentException("atomic calibration activation must preserve identity and increment a validated version exactly once");
        }
        String payload = PostgresJsonArtifactRepository.validJson(codec.encode(replacement));
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Lock the currently active target profile so only one replacement can retire it.
                try (PreparedStatement q = c.prepareStatement("""
                        SELECT payload::text FROM b6_uncertainty_calibration_profile
                        WHERE scope_id=? AND target_id=? AND status='ACTIVE' FOR UPDATE
                        """)) {
                    q.setString(1, scopeId); q.setString(2, replacement.targetId());
                    try (ResultSet rows = q.executeQuery()) {
                        while (rows.next()) {
                            UncertaintyCalibrationProfile previous = codec.decode(rows.getString(1));
                            if (previous.id().equals(id)) {
                                throw new SecurityException("validated candidate cannot already be the active calibration profile");
                            }
                            UncertaintyCalibrationProfile retired = previous.retire(replacement.activatedAt());
                            if (!updateProfile(c, previous.version(), retired)) {
                                throw new IllegalStateException("active calibration profile changed during atomic replacement");
                            }
                        }
                    }
                }
                try (PreparedStatement q = c.prepareStatement("""
                        UPDATE b6_uncertainty_calibration_profile SET target_id=?,profile_version=?,status=?,
                            training_dataset_fingerprint=?,validation_dataset_fingerprint=?,training_sample_count=?,
                            validation_sample_count=?,validation_ece=?,payload=CAST(? AS jsonb),updated_at=?
                        WHERE scope_id=? AND profile_id=? AND profile_version=? AND status='VALIDATED'
                        """)) {
                    q.setString(1, replacement.targetId()); q.setLong(2, replacement.version());
                    q.setString(3, replacement.status().name()); q.setString(4, replacement.trainingDatasetFingerprint());
                    q.setString(5, replacement.validationDatasetFingerprint().isBlank() ? null : replacement.validationDatasetFingerprint());
                    q.setLong(6, replacement.trainingSampleCount()); q.setLong(7, replacement.validationSampleCount());
                    q.setDouble(8, replacement.validationExpectedCalibrationError()); q.setString(9, payload);
                    PostgresJdbc.setInstant(q, 10, replacement.activatedAt());
                    q.setString(11, scopeId); q.setString(12, id); q.setLong(13, expectedVersion);
                    if (q.executeUpdate() != 1) { c.rollback(); return false; }
                }
                c.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                try { c.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                if (failure instanceof SQLException sql && "23505".equals(sql.getSQLState())) return false;
                throw failure;
            }
        } catch (SQLException e) {
            throw new PostgresPersistenceException("PostgreSQL B6 calibration activation transaction failed", e);
        }
    }

    private boolean updateProfile(Connection c, long expectedVersion,
            UncertaintyCalibrationProfile replacement) throws SQLException {
        String payload = PostgresJsonArtifactRepository.validJson(codec.encode(replacement));
        try (PreparedStatement q = c.prepareStatement("""
                UPDATE b6_uncertainty_calibration_profile SET target_id=?,profile_version=?,status=?,
                    training_dataset_fingerprint=?,validation_dataset_fingerprint=?,training_sample_count=?,
                    validation_sample_count=?,validation_ece=?,payload=CAST(? AS jsonb),updated_at=?
                WHERE scope_id=? AND profile_id=? AND profile_version=?
                """)) {
            q.setString(1, replacement.targetId()); q.setLong(2, replacement.version()); q.setString(3, replacement.status().name());
            q.setString(4, replacement.trainingDatasetFingerprint());
            q.setString(5, replacement.validationDatasetFingerprint().isBlank() ? null : replacement.validationDatasetFingerprint());
            q.setLong(6, replacement.trainingSampleCount()); q.setLong(7, replacement.validationSampleCount());
            q.setDouble(8, replacement.validationExpectedCalibrationError()); q.setString(9, payload);
            PostgresJdbc.setInstant(q, 10, replacement.activatedAt()); q.setString(11, scopeId);
            q.setString(12, replacement.id()); q.setLong(13, expectedVersion);
            return q.executeUpdate() == 1;
        }
    }

    @Override public List<UncertaintyCalibrationProfile> history(MemoryScopeId scope, String targetId, int maximum) {
        requireScope(scope);
        if (maximum < 1 || maximum > 10_000) throw new IllegalArgumentException("maximum must be in 1..10000");
        try (Connection c = dataSource.getConnection(); PreparedStatement q = c.prepareStatement("""
                SELECT payload::text FROM b6_uncertainty_calibration_profile
                WHERE scope_id=? AND target_id=? ORDER BY created_at,profile_id LIMIT ?
                """)) {
            q.setString(1, scopeId); q.setString(2, required(targetId, "targetId")); q.setInt(3, maximum);
            List<UncertaintyCalibrationProfile> result = new ArrayList<>();
            try (ResultSet r = q.executeQuery()) { while (r.next()) result.add(codec.decode(r.getString(1))); }
            return List.copyOf(result);
        } catch (SQLException e) { throw new PostgresPersistenceException("PostgreSQL B6 calibration history query failed", e); }
    }

    private void bind(PreparedStatement q, UncertaintyCalibrationProfile p, String payload, int start) throws SQLException {
        q.setString(start, scopeId); q.setString(start + 1, p.id()); q.setString(start + 2, p.targetId());
        q.setLong(start + 3, p.version()); q.setString(start + 4, p.status().name());
        q.setString(start + 5, p.trainingDatasetFingerprint());
        q.setString(start + 6, p.validationDatasetFingerprint().isBlank() ? null : p.validationDatasetFingerprint());
        q.setLong(start + 7, p.trainingSampleCount()); q.setLong(start + 8, p.validationSampleCount());
        q.setDouble(start + 9, p.validationExpectedCalibrationError()); q.setString(start + 10, payload);
        PostgresJdbc.setInstant(q, start + 11, p.createdAt());
        PostgresJdbc.setInstant(q, start + 12, p.activatedAt() != null ? p.activatedAt()
                : p.validatedAt() != null ? p.validatedAt() : p.createdAt());
    }
    private void requireScope(MemoryScopeId scope) {
        if (!scopeId.equals(Objects.requireNonNull(scope, "scope").value())) throw new SecurityException("B6 calibration scope mismatch");
    }
    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank"); return result;
    }
}
