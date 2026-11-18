package dev.supplychain.agent.infrastructure.postgres.memory;

import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryConflictException;
import dev.supplychain.agent.memory.common.MemoryNotFoundException;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.MemoryVersionConflictException;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.semantic.FactStatus;
import dev.supplychain.agent.memory.semantic.FactValue;
import dev.supplychain.agent.memory.semantic.SemanticFact;
import dev.supplychain.agent.memory.semantic.SemanticFactId;
import dev.supplychain.agent.memory.semantic.SemanticFactQuery;
import dev.supplychain.agent.memory.semantic.SemanticFactRepository;
import dev.supplychain.agent.memory.semantic.SemanticFactRevision;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Transactional PostgreSQL adapter preserving M2 semantic-fact lifecycle and temporal semantics. */
public final class PostgresSemanticFactRepository extends JdbcRepositorySupport implements SemanticFactRepository {
    private static final String COLUMNS = "fact_id, scope_id, version, subject_type, subject_id, predicate, value_type, "
            + "text_value, decimal_value, flag_value, timestamp_value, entity_type, entity_id, valid_from, valid_until, "
            + "recorded_at, status, provenance_source, provenance_source_id, provenance_observed_at, "
            + "provenance_source_version, activated_by, activated_at, activation_evidence_type, activation_evidence_id, "
            + "supersedes_fact_id, superseded_at, rejected_by, rejected_at, rejection_evidence_type, rejection_evidence_id";

    public PostgresSemanticFactRepository(DataSource dataSource) { super(dataSource); }

    @Override public SemanticFact stage(SemanticFact fact) {
        Objects.requireNonNull(fact, "fact");
        if (fact.status() != FactStatus.PROPOSED || fact.version() != 0) {
            throw new IllegalArgumentException("only new proposed semantic facts can be staged");
        }
        return transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO coala_semantic_fact (" + COLUMNS + ") VALUES ("
                    + placeholders(31) + ")")) {
                bind(statement, fact);
                statement.executeUpdate();
            }
            return fact;
        });
    }

    @Override public Optional<SemanticFact> findById(SemanticFactId id) {
        Objects.requireNonNull(id, "id");
        return read(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM coala_semantic_fact WHERE fact_id = ?")) {
                statement.setString(1, id.value());
                try (ResultSet rows = statement.executeQuery()) { return rows.next() ? Optional.of(map(rows)) : Optional.empty(); }
            }
        });
    }

    @Override public SemanticFact activate(SemanticFactId id, long expectedVersion, String actor,
                                           EvidenceReference approvalEvidence, Instant at) {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(approvalEvidence, "approvalEvidence"); Objects.requireNonNull(at, "at");
        return transaction(connection -> {
            SemanticFact keySnapshot = requireById(connection, id);
            lockKey(connection, keySnapshot);
            SemanticFact current = requireForUpdate(connection, id);
            checkVersion(current, expectedVersion);
            SemanticFact activated = current.activate(actor, approvalEvidence, at, null);
            assertNoConflictingActiveFacts(connection, activated, Set.of());
            update(connection, activated, expectedVersion);
            return activated;
        });
    }

    @Override public SemanticFact supersedeAndActivate(SemanticFactId oldId, long expectedOldVersion,
                                                        SemanticFactId newId, long expectedNewVersion, String actor,
                                                        EvidenceReference approvalEvidence, Instant at) {
        return supersedeAndActivateSegments(oldId, expectedOldVersion,
                List.of(new SemanticFactRevision(newId, expectedNewVersion)), actor, approvalEvidence, at).getFirst();
    }

    @Override public List<SemanticFact> supersedeAndActivateSegments(SemanticFactId oldId, long expectedOldVersion,
                                                                     List<SemanticFactRevision> replacements,
                                                                     String actor, EvidenceReference approvalEvidence,
                                                                     Instant at) {
        Objects.requireNonNull(oldId, "oldId"); Objects.requireNonNull(replacements, "replacements");
        Objects.requireNonNull(approvalEvidence, "approvalEvidence"); Objects.requireNonNull(at, "at");
        if (replacements.isEmpty()) throw new IllegalArgumentException("at least one replacement segment is required");
        return transaction(connection -> {
            SemanticFact keySnapshot = requireById(connection, oldId);
            lockKey(connection, keySnapshot);
            SemanticFact oldFact = requireForUpdate(connection, oldId);
            checkVersion(oldFact, expectedOldVersion);
            if (oldFact.status() != FactStatus.ACTIVE) throw new IllegalStateException("only an active fact can be temporally amended");

            Set<SemanticFactId> replacementIds = new HashSet<>();
            List<SemanticFact> proposed = new ArrayList<>();
            for (SemanticFactRevision revision : replacements) {
                if (!replacementIds.add(revision.factId())) throw new IllegalArgumentException("replacement fact ids must be unique");
                SemanticFact candidate = requireForUpdate(connection, revision.factId());
                checkVersion(candidate, revision.expectedVersion());
                if (candidate.status() != FactStatus.PROPOSED || !candidate.key().equals(oldFact.key())) {
                    throw new IllegalStateException("every replacement must be proposed and share the old semantic key");
                }
                proposed.add(candidate);
            }
            proposed.sort(Comparator.comparing(SemanticFact::validFrom));
            validateCoverage(oldFact, proposed);

            List<SemanticFact> activated = proposed.stream()
                    .map(fact -> fact.activate(actor, approvalEvidence, at, oldId)).toList();
            for (SemanticFact candidate : activated) assertNoConflictingActiveFacts(connection, candidate,
                    replacementIdsWithOld(oldId, replacementIds));
            SemanticFact superseded = oldFact.supersede(at);
            update(connection, superseded, expectedOldVersion);
            for (int index = 0; index < activated.size(); index++) {
                SemanticFact original = proposed.get(index);
                update(connection, activated.get(index), original.version());
            }
            return List.copyOf(activated);
        });
    }

    @Override public SemanticFact reject(SemanticFactId id, long expectedVersion, String reviewer,
                                         EvidenceReference reviewEvidence, Instant at) {
        Objects.requireNonNull(reviewEvidence, "reviewEvidence"); Objects.requireNonNull(at, "at");
        return transaction(connection -> {
            SemanticFact current = requireForUpdate(connection, id);
            checkVersion(current, expectedVersion);
            SemanticFact rejected = current.reject(reviewer, reviewEvidence, at);
            update(connection, rejected, expectedVersion);
            return rejected;
        });
    }

    @Override public List<SemanticFact> findActive(SemanticFactQuery query) {
        Objects.requireNonNull(query, "query");
        return read(connection -> {
            String sql = "SELECT " + COLUMNS + " FROM coala_semantic_fact WHERE scope_id = ? AND subject_type = ? AND subject_id = ? "
                    + "AND (? IS NULL OR predicate = ?) AND valid_from <= ? AND (valid_until IS NULL OR ? < valid_until) "
                    + "AND activated_at <= ? AND (status = 'ACTIVE' OR (status = 'SUPERSEDED' AND ? < superseded_at)) "
                    + "ORDER BY valid_from DESC, fact_id LIMIT ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, query.scopeId().value());
                statement.setString(2, query.subject().entityType());
                statement.setString(3, query.subject().entityId());
                statement.setString(4, query.predicate());
                statement.setString(5, query.predicate());
                statement.setTimestamp(6, timestamp(query.validAt()));
                statement.setTimestamp(7, timestamp(query.validAt()));
                statement.setTimestamp(8, timestamp(query.knownAt()));
                statement.setTimestamp(9, timestamp(query.knownAt()));
                statement.setInt(10, query.limit());
                try (ResultSet rows = statement.executeQuery()) {
                    List<SemanticFact> values = new ArrayList<>();
                    while (rows.next()) values.add(map(rows));
                    return List.copyOf(values);
                }
            }
        });
    }

    @Override public int deleteExpiredSuperseded(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        return transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM coala_semantic_fact WHERE status = 'SUPERSEDED' AND superseded_at <= ?")) {
                statement.setTimestamp(1, timestamp(cutoff));
                return statement.executeUpdate();
            }
        });
    }

    private static void validateCoverage(SemanticFact oldFact, List<SemanticFact> segments) {
        Instant expectedStart = oldFact.validFrom();
        for (int index = 0; index < segments.size(); index++) {
            SemanticFact segment = segments.get(index);
            if (!segment.validFrom().equals(expectedStart)) {
                throw new IllegalArgumentException("replacement segments must be contiguous and cover the old validity interval");
            }
            expectedStart = segment.validUntil();
            if (expectedStart == null && index != segments.size() - 1) {
                throw new IllegalArgumentException("an unbounded replacement segment must be last");
            }
        }
        if (!Objects.equals(expectedStart, oldFact.validUntil())) {
            throw new IllegalArgumentException("replacement segments must preserve the complete old validity interval");
        }
    }

    private static Set<SemanticFactId> replacementIdsWithOld(SemanticFactId oldId, Set<SemanticFactId> ids) {
        Set<SemanticFactId> excluded = new HashSet<>(ids);
        excluded.add(oldId);
        return excluded;
    }

    private void assertNoConflictingActiveFacts(java.sql.Connection connection, SemanticFact candidate,
                                                Set<SemanticFactId> excluded) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM coala_semantic_fact WHERE scope_id = ? AND subject_type = ? AND subject_id = ? "
                + "AND predicate = ? AND status = 'ACTIVE' AND (valid_until IS NULL OR valid_until > ?) "
                + "AND (? IS NULL OR valid_from < ?) FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, candidate.scopeId().value());
            statement.setString(2, candidate.subject().entityType());
            statement.setString(3, candidate.subject().entityId());
            statement.setString(4, candidate.predicate());
            statement.setTimestamp(5, timestamp(candidate.validFrom()));
            Timestamp end = timestamp(candidate.validUntil());
            if (end == null) { statement.setNull(6, Types.TIMESTAMP_WITH_TIMEZONE); statement.setNull(7, Types.TIMESTAMP_WITH_TIMEZONE); }
            else { statement.setTimestamp(6, end); statement.setTimestamp(7, end); }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    SemanticFact existing = map(rows);
                    if (!excluded.contains(existing.id()) && !existing.value().equals(candidate.value())) {
                        throw new MemoryConflictException("Conflicting active semantic fact for " + candidate.key());
                    }
                }
            }
        }
    }

    private static void lockKey(java.sql.Connection connection, SemanticFact fact) throws SQLException {
        String key = fact.scopeId().value() + '\u001f' + fact.subject().entityType() + '\u001f'
                + fact.subject().entityId() + '\u001f' + fact.predicate();
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            statement.setString(1, key);
            statement.execute();
        }
    }

    private SemanticFact requireById(java.sql.Connection connection, SemanticFactId id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM coala_semantic_fact WHERE fact_id = ?")) {
            statement.setString(1, id.value());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new MemoryNotFoundException("Semantic fact not found: " + id.value());
                return map(rows);
            }
        }
    }

    private SemanticFact requireForUpdate(java.sql.Connection connection, SemanticFactId id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM coala_semantic_fact WHERE fact_id = ? FOR UPDATE")) {
            statement.setString(1, id.value());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new MemoryNotFoundException("Semantic fact not found: " + id.value());
                return map(rows);
            }
        }
    }

    private static void update(java.sql.Connection connection, SemanticFact fact, long expectedVersion) throws SQLException {
        String sql = "UPDATE coala_semantic_fact SET version = ?, status = ?, activated_by = ?, activated_at = ?, "
                + "activation_evidence_type = ?, activation_evidence_id = ?, supersedes_fact_id = ?, superseded_at = ?, "
                + "rejected_by = ?, rejected_at = ?, rejection_evidence_type = ?, rejection_evidence_id = ? "
                + "WHERE fact_id = ? AND version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, fact.version()); statement.setString(2, fact.status().name());
            statement.setString(3, fact.activatedBy()); statement.setTimestamp(4, timestamp(fact.activatedAt()));
            statement.setString(5, fact.activationEvidence() == null ? null : fact.activationEvidence().evidenceType());
            statement.setString(6, fact.activationEvidence() == null ? null : fact.activationEvidence().evidenceId());
            statement.setString(7, fact.supersedes() == null ? null : fact.supersedes().value());
            statement.setTimestamp(8, timestamp(fact.supersededAt())); statement.setString(9, fact.rejectedBy());
            statement.setTimestamp(10, timestamp(fact.rejectedAt()));
            statement.setString(11, fact.rejectionEvidence() == null ? null : fact.rejectionEvidence().evidenceType());
            statement.setString(12, fact.rejectionEvidence() == null ? null : fact.rejectionEvidence().evidenceId());
            statement.setString(13, fact.id().value()); statement.setLong(14, expectedVersion);
            if (statement.executeUpdate() != 1) throw new MemoryVersionConflictException("Semantic fact revision changed during update: " + fact.id().value());
        }
    }

    private static void bind(PreparedStatement statement, SemanticFact fact) throws SQLException {
        int i = 1;
        statement.setString(i++, fact.id().value()); statement.setString(i++, fact.scopeId().value()); statement.setLong(i++, fact.version());
        statement.setString(i++, fact.subject().entityType()); statement.setString(i++, fact.subject().entityId());
        statement.setString(i++, fact.predicate());
        FactValue value = fact.value();
        statement.setString(i++, valueType(value));
        statement.setString(i++, value instanceof FactValue.Text v ? v.value() : null);
        if (value instanceof FactValue.Decimal v) statement.setBigDecimal(i++, v.value()); else statement.setNull(i++, Types.NUMERIC);
        if (value instanceof FactValue.Flag v) statement.setBoolean(i++, v.value()); else statement.setNull(i++, Types.BOOLEAN);
        statement.setTimestamp(i++, value instanceof FactValue.Timestamp v ? timestamp(v.value()) : null);
        statement.setString(i++, value instanceof FactValue.EntityLink v ? v.value().entityType() : null);
        statement.setString(i++, value instanceof FactValue.EntityLink v ? v.value().entityId() : null);
        statement.setTimestamp(i++, timestamp(fact.validFrom())); statement.setTimestamp(i++, timestamp(fact.validUntil()));
        statement.setTimestamp(i++, timestamp(fact.recordedAt())); statement.setString(i++, fact.status().name());
        statement.setString(i++, fact.provenance().source().name()); statement.setString(i++, fact.provenance().sourceId());
        statement.setTimestamp(i++, timestamp(fact.provenance().observedAt())); statement.setString(i++, fact.provenance().sourceVersion());
        statement.setString(i++, fact.activatedBy()); statement.setTimestamp(i++, timestamp(fact.activatedAt()));
        statement.setString(i++, fact.activationEvidence() == null ? null : fact.activationEvidence().evidenceType());
        statement.setString(i++, fact.activationEvidence() == null ? null : fact.activationEvidence().evidenceId());
        statement.setString(i++, fact.supersedes() == null ? null : fact.supersedes().value());
        statement.setTimestamp(i++, timestamp(fact.supersededAt())); statement.setString(i++, fact.rejectedBy());
        statement.setTimestamp(i++, timestamp(fact.rejectedAt()));
        statement.setString(i++, fact.rejectionEvidence() == null ? null : fact.rejectionEvidence().evidenceType());
        statement.setString(i, fact.rejectionEvidence() == null ? null : fact.rejectionEvidence().evidenceId());
    }

    private static String valueType(FactValue value) {
        if (value instanceof FactValue.Text) return "TEXT";
        if (value instanceof FactValue.Decimal) return "DECIMAL";
        if (value instanceof FactValue.Flag) return "FLAG";
        if (value instanceof FactValue.Timestamp) return "TIMESTAMP";
        if (value instanceof FactValue.EntityLink) return "ENTITY_LINK";
        throw new IllegalArgumentException("Unsupported semantic value type: " + value.getClass().getName());
    }

    private static SemanticFact map(ResultSet row) throws SQLException {
        String type = row.getString("value_type");
        FactValue value = switch (type) {
            case "TEXT" -> new FactValue.Text(row.getString("text_value"));
            case "DECIMAL" -> new FactValue.Decimal(row.getBigDecimal("decimal_value"));
            case "FLAG" -> new FactValue.Flag(row.getBoolean("flag_value"));
            case "TIMESTAMP" -> new FactValue.Timestamp(instant(row.getTimestamp("timestamp_value")));
            case "ENTITY_LINK" -> new FactValue.EntityLink(new EntityReference(row.getString("entity_type"), row.getString("entity_id")));
            default -> throw new IllegalStateException("Unknown semantic fact value type: " + type);
        };
        Provenance provenance = new Provenance(ProvenanceSource.valueOf(row.getString("provenance_source")),
                row.getString("provenance_source_id"), instant(row.getTimestamp("provenance_observed_at")),
                instant(row.getTimestamp("recorded_at")), row.getString("provenance_source_version"));
        return new SemanticFact(new SemanticFactId(row.getString("fact_id")), new MemoryScopeId(row.getString("scope_id")),
                row.getLong("version"), new EntityReference(row.getString("subject_type"), row.getString("subject_id")),
                row.getString("predicate"), value, instant(row.getTimestamp("valid_from")),
                instant(row.getTimestamp("valid_until")), instant(row.getTimestamp("recorded_at")),
                FactStatus.valueOf(row.getString("status")), provenance,
                row.getString("activated_by"), instant(row.getTimestamp("activated_at")),
                evidence(row.getString("activation_evidence_type"), row.getString("activation_evidence_id")),
                row.getString("supersedes_fact_id") == null ? null : new SemanticFactId(row.getString("supersedes_fact_id")),
                instant(row.getTimestamp("superseded_at")), row.getString("rejected_by"),
                instant(row.getTimestamp("rejected_at")), evidence(row.getString("rejection_evidence_type"), row.getString("rejection_evidence_id")));
    }

    private static EvidenceReference evidence(String type, String id) {
        return type == null ? null : new EvidenceReference(type, id);
    }
    private static void checkVersion(SemanticFact fact, long expected) {
        if (fact.version() != expected) throw new MemoryVersionConflictException(
                "Fact version changed from " + expected + " to " + fact.version());
    }
    @Override protected RuntimeException translate(SQLException ex) {
        if ("23505".equals(ex.getSQLState())) return new MemoryConflictException("Semantic fact uniqueness or overlap constraint conflict");
        return super.translate(ex);
    }
    private static String placeholders(int count) { return String.join(",", java.util.Collections.nCopies(count, "?")); }
}
