package dev.supplychain.agent.infrastructure.postgres.memory;

import dev.supplychain.agent.memory.common.MemoryConflictException;
import dev.supplychain.agent.memory.common.MemoryNotFoundException;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.MemoryVersionConflictException;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.procedural.ProcedureArtifactReference;
import dev.supplychain.agent.memory.procedural.ProcedureKind;
import dev.supplychain.agent.memory.procedural.ProcedureStatus;
import dev.supplychain.agent.memory.procedural.ProcedureVersion;
import dev.supplychain.agent.memory.procedural.ProcedureVersionId;
import dev.supplychain.agent.memory.procedural.ProceduralMemoryRepository;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Transactional PostgreSQL adapter for append-only procedural versions and audited lifecycle transitions. */
public final class PostgresProceduralMemoryRepository extends JdbcRepositorySupport implements ProceduralMemoryRepository {
    private static final String COLUMNS = "procedure_version_id, scope_id, procedure_key, version_number, kind, description, "
            + "artifact_location, artifact_sha256, provenance_source, provenance_source_id, provenance_observed_at, "
            + "provenance_recorded_at, provenance_source_version, status, lifecycle_revision, created_at, created_by, "
            + "validated_at, validated_by, activated_at, activated_by, retired_at, rejected_at, rejected_by, predecessor_id";

    public PostgresProceduralMemoryRepository(DataSource dataSource) { super(dataSource); }

    @Override public ProcedureVersion stage(ProcedureVersion draft) {
        Objects.requireNonNull(draft, "draft");
        if (draft.status() != ProcedureStatus.DRAFT || draft.lifecycleRevision() != 0) {
            throw new IllegalArgumentException("only new draft procedure versions can be staged");
        }
        return transaction(connection -> {
            lockKey(connection, draft.scopeId(), draft.procedureKey());
            ProcedureVersion latest = latest(connection, draft.scopeId(), draft.procedureKey(), true).orElse(null);
            int expectedNumber = latest == null ? 1 : latest.versionNumber() + 1;
            if (draft.versionNumber() != expectedNumber
                    || (latest == null && draft.predecessor() != null)
                    || (latest != null && !latest.id().equals(draft.predecessor()))) {
                throw new MemoryConflictException("Procedure version must append after the latest immutable version; expected v"
                        + expectedNumber);
            }
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO coala_procedure_version (" + COLUMNS
                    + ") VALUES (" + placeholders(25) + ")")) {
                bind(statement, draft);
                statement.executeUpdate();
            }
            return draft;
        });
    }

    @Override public Optional<ProcedureVersion> findById(ProcedureVersionId id) {
        Objects.requireNonNull(id, "id");
        return read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM coala_procedure_version WHERE procedure_version_id = ?")) {
                statement.setString(1, id.value());
                try (ResultSet rows = statement.executeQuery()) { return rows.next() ? Optional.of(map(rows)) : Optional.empty(); }
            }
        });
    }

    @Override public List<ProcedureVersion> findVersions(MemoryScopeId scope, String procedureKey) {
        Objects.requireNonNull(scope, "scope");
        return read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS
                    + " FROM coala_procedure_version WHERE scope_id = ? AND procedure_key = ? ORDER BY version_number DESC")) {
                statement.setString(1, scope.value()); statement.setString(2, required(procedureKey, "procedureKey"));
                try (ResultSet rows = statement.executeQuery()) { return collect(rows); }
            }
        });
    }

    @Override public ProcedureVersion compareAndSet(ProcedureVersionId id, long expectedRevision, ProcedureVersion replacement) {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(replacement, "replacement");
        return transaction(connection -> {
            ProcedureVersion current = requireForUpdate(connection, id);
            checkRevision(current, expectedRevision);
            boolean allowed = current.status() == ProcedureStatus.DRAFT
                    && (replacement.status() == ProcedureStatus.VALIDATED || replacement.status() == ProcedureStatus.REJECTED);
            if (!allowed || !sameImmutableIdentity(current, replacement)
                    || replacement.lifecycleRevision() != expectedRevision + 1) {
                throw new IllegalArgumentException("lifecycle update must be a valid draft transition and preserve immutable procedure identity/artifact");
            }
            update(connection, replacement, expectedRevision);
            return replacement;
        });
    }

    @Override public ProcedureVersion activate(ProcedureVersionId id, long expectedRevision, String approver, Instant at) {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(at, "at");
        return transaction(connection -> {
            ProcedureVersion keySnapshot = requireById(connection, id);
            lockKey(connection, keySnapshot.scopeId(), keySnapshot.procedureKey());
            // Every writer acquires the logical key before a row lock to avoid stage/activate deadlocks.
            ProcedureVersion candidate = requireForUpdate(connection, id);
            checkRevision(candidate, expectedRevision);
            if (candidate.status() != ProcedureStatus.VALIDATED) {
                throw new IllegalStateException("only validated procedure versions can be activated");
            }
            ProcedureVersion previous = activeForUpdate(connection, candidate.scopeId(), candidate.procedureKey()).orElse(null);
            ProcedureVersion active = candidate.activate(approver, at);
            if (previous != null) update(connection, previous.retire(at), previous.lifecycleRevision());
            update(connection, active, expectedRevision);
            return active;
        });
    }

    @Override public Optional<ProcedureVersion> findActive(MemoryScopeId scope, String procedureKey) {
        Objects.requireNonNull(scope, "scope");
        return read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS
                    + " FROM coala_procedure_version WHERE scope_id = ? AND procedure_key = ? AND status = 'ACTIVE'")) {
                statement.setString(1, scope.value()); statement.setString(2, required(procedureKey, "procedureKey"));
                try (ResultSet rows = statement.executeQuery()) { return rows.next() ? Optional.of(map(rows)) : Optional.empty(); }
            }
        });
    }

    @Override public List<ProcedureVersion> findActive(MemoryScopeId scope, int limit) {
        validateLimit(limit); Objects.requireNonNull(scope, "scope");
        return read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS
                    + " FROM coala_procedure_version WHERE scope_id = ? AND status = 'ACTIVE' "
                    + "ORDER BY procedure_key, version_number LIMIT ?")) {
                statement.setString(1, scope.value()); statement.setInt(2, limit);
                try (ResultSet rows = statement.executeQuery()) { return collect(rows); }
            }
        });
    }

    @Override public List<ProcedureVersion> findActiveAt(MemoryScopeId scope, Instant knownAt, int limit) {
        validateLimit(limit); Objects.requireNonNull(scope, "scope"); Objects.requireNonNull(knownAt, "knownAt");
        return read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS
                    + " FROM coala_procedure_version WHERE scope_id = ? AND activated_at <= ? "
                    + "AND (status = 'ACTIVE' OR (status = 'RETIRED' AND ? < retired_at)) "
                    + "ORDER BY procedure_key, version_number LIMIT ?")) {
                statement.setString(1, scope.value()); statement.setTimestamp(2, timestamp(knownAt));
                statement.setTimestamp(3, timestamp(knownAt)); statement.setInt(4, limit);
                try (ResultSet rows = statement.executeQuery()) { return collect(rows); }
            }
        });
    }

    private static Optional<ProcedureVersion> latest(java.sql.Connection connection, MemoryScopeId scope,
                                                      String key, boolean lock) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM coala_procedure_version WHERE scope_id = ? AND procedure_key = ? "
                + "ORDER BY version_number DESC LIMIT 1" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, scope.value()); statement.setString(2, key);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? Optional.of(map(rows)) : Optional.empty(); }
        }
    }

    private static Optional<ProcedureVersion> activeForUpdate(java.sql.Connection connection, MemoryScopeId scope,
                                                                String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS
                + " FROM coala_procedure_version WHERE scope_id = ? AND procedure_key = ? AND status = 'ACTIVE' FOR UPDATE")) {
            statement.setString(1, scope.value()); statement.setString(2, key);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? Optional.of(map(rows)) : Optional.empty(); }
        }
    }

    private static void lockKey(java.sql.Connection connection, MemoryScopeId scope, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            statement.setString(1, scope.value() + '\u001f' + key);
            statement.execute();
        }
    }

    private static ProcedureVersion requireById(java.sql.Connection connection, ProcedureVersionId id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS
                + " FROM coala_procedure_version WHERE procedure_version_id = ?")) {
            statement.setString(1, id.value());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new MemoryNotFoundException("Procedure version not found: " + id.value());
                return map(rows);
            }
        }
    }

    private static ProcedureVersion requireForUpdate(java.sql.Connection connection, ProcedureVersionId id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS
                + " FROM coala_procedure_version WHERE procedure_version_id = ? FOR UPDATE")) {
            statement.setString(1, id.value());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new MemoryNotFoundException("Procedure version not found: " + id.value());
                return map(rows);
            }
        }
    }

    private static void update(java.sql.Connection connection, ProcedureVersion value, long expectedRevision) throws SQLException {
        String sql = "UPDATE coala_procedure_version SET status = ?, lifecycle_revision = ?, validated_at = ?, validated_by = ?, "
                + "activated_at = ?, activated_by = ?, retired_at = ?, rejected_at = ?, rejected_by = ? "
                + "WHERE procedure_version_id = ? AND lifecycle_revision = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value.status().name()); statement.setLong(2, value.lifecycleRevision());
            statement.setTimestamp(3, timestamp(value.validatedAt())); statement.setString(4, value.validatedBy());
            statement.setTimestamp(5, timestamp(value.activatedAt())); statement.setString(6, value.activatedBy());
            statement.setTimestamp(7, timestamp(value.retiredAt())); statement.setTimestamp(8, timestamp(value.rejectedAt()));
            statement.setString(9, value.rejectedBy()); statement.setString(10, value.id().value()); statement.setLong(11, expectedRevision);
            if (statement.executeUpdate() != 1) throw new MemoryVersionConflictException("Procedure lifecycle revision changed during update: " + value.id().value());
        }
    }

    private static boolean sameImmutableIdentity(ProcedureVersion a, ProcedureVersion b) {
        return a.id().equals(b.id()) && a.scopeId().equals(b.scopeId()) && a.procedureKey().equals(b.procedureKey())
                && a.versionNumber() == b.versionNumber() && a.kind() == b.kind() && a.description().equals(b.description())
                && a.artifact().equals(b.artifact()) && a.provenance().equals(b.provenance())
                && a.createdAt().equals(b.createdAt()) && a.createdBy().equals(b.createdBy())
                && Objects.equals(a.predecessor(), b.predecessor());
    }

    private static void bind(PreparedStatement statement, ProcedureVersion value) throws SQLException {
        int i = 1;
        statement.setString(i++, value.id().value()); statement.setString(i++, value.scopeId().value());
        statement.setString(i++, value.procedureKey()); statement.setInt(i++, value.versionNumber());
        statement.setString(i++, value.kind().name()); statement.setString(i++, value.description());
        statement.setString(i++, value.artifact().location().toString()); statement.setString(i++, value.artifact().sha256());
        statement.setString(i++, value.provenance().source().name()); statement.setString(i++, value.provenance().sourceId());
        statement.setTimestamp(i++, timestamp(value.provenance().observedAt())); statement.setTimestamp(i++, timestamp(value.provenance().recordedAt()));
        statement.setString(i++, value.provenance().sourceVersion()); statement.setString(i++, value.status().name());
        statement.setLong(i++, value.lifecycleRevision()); statement.setTimestamp(i++, timestamp(value.createdAt()));
        statement.setString(i++, value.createdBy()); statement.setTimestamp(i++, timestamp(value.validatedAt()));
        statement.setString(i++, value.validatedBy()); statement.setTimestamp(i++, timestamp(value.activatedAt()));
        statement.setString(i++, value.activatedBy()); statement.setTimestamp(i++, timestamp(value.retiredAt()));
        statement.setTimestamp(i++, timestamp(value.rejectedAt())); statement.setString(i++, value.rejectedBy());
        statement.setString(i, value.predecessor() == null ? null : value.predecessor().value());
    }

    private static ProcedureVersion map(ResultSet row) throws SQLException {
        Provenance provenance = new Provenance(ProvenanceSource.valueOf(row.getString("provenance_source")),
                row.getString("provenance_source_id"), instant(row.getTimestamp("provenance_observed_at")),
                instant(row.getTimestamp("provenance_recorded_at")), row.getString("provenance_source_version"));
        String predecessor = row.getString("predecessor_id");
        return new ProcedureVersion(new ProcedureVersionId(row.getString("procedure_version_id")),
                new MemoryScopeId(row.getString("scope_id")), row.getString("procedure_key"), row.getInt("version_number"),
                ProcedureKind.valueOf(row.getString("kind")), row.getString("description"),
                new ProcedureArtifactReference(URI.create(row.getString("artifact_location")), row.getString("artifact_sha256")),
                provenance, ProcedureStatus.valueOf(row.getString("status")), row.getLong("lifecycle_revision"),
                instant(row.getTimestamp("created_at")), row.getString("created_by"),
                instant(row.getTimestamp("validated_at")), row.getString("validated_by"),
                instant(row.getTimestamp("activated_at")), row.getString("activated_by"),
                instant(row.getTimestamp("retired_at")), instant(row.getTimestamp("rejected_at")),
                row.getString("rejected_by"), predecessor == null ? null : new ProcedureVersionId(predecessor));
    }

    private static List<ProcedureVersion> collect(ResultSet rows) throws SQLException {
        java.util.ArrayList<ProcedureVersion> result = new java.util.ArrayList<>();
        while (rows.next()) result.add(map(rows));
        return List.copyOf(result);
    }
    private static void checkRevision(ProcedureVersion value, long expected) {
        if (value.lifecycleRevision() != expected) throw new MemoryVersionConflictException(
                "Procedure lifecycle revision changed from " + expected + " to " + value.lifecycleRevision());
    }
    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be in [1,500]");
    }
    private static String placeholders(int count) { return String.join(",", java.util.Collections.nCopies(count, "?")); }
    private static String required(String value, String name) {
        String cleaned = Objects.requireNonNull(value, name).trim();
        if (cleaned.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return cleaned;
    }
    @Override protected RuntimeException translate(SQLException ex) {
        if ("23505".equals(ex.getSQLState())) return new MemoryConflictException("Procedural version uniqueness conflict");
        return super.translate(ex);
    }
}
