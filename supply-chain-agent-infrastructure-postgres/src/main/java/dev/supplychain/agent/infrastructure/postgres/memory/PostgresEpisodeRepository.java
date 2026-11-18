package dev.supplychain.agent.infrastructure.postgres.memory;

import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryConflictException;
import dev.supplychain.agent.memory.common.MemoryNotFoundException;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.MemoryVersionConflictException;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.episodic.Episode;
import dev.supplychain.agent.memory.episodic.EpisodeEvent;
import dev.supplychain.agent.memory.episodic.EpisodeId;
import dev.supplychain.agent.memory.episodic.EpisodeOutcome;
import dev.supplychain.agent.memory.episodic.EpisodeQuery;
import dev.supplychain.agent.memory.episodic.EpisodeRepository;
import dev.supplychain.agent.memory.episodic.EpisodeStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Durable JDBC implementation of append-oriented episodic memory and review lifecycle. */
public final class PostgresEpisodeRepository extends JdbcRepositorySupport implements EpisodeRepository {
    public PostgresEpisodeRepository(DataSource dataSource) { super(dataSource); }

    @Override public Episode append(Episode episode) {
        Objects.requireNonNull(episode, "episode");
        return transaction(connection -> { insertEpisode(connection, episode); return episode; });
    }

    @Override public Episode appendIdempotent(Episode episode, EvidenceReference idempotencyReference) {
        Objects.requireNonNull(episode, "episode");
        Objects.requireNonNull(idempotencyReference, "idempotencyReference");
        if (!episode.evidence().contains(idempotencyReference)) {
            throw new IllegalArgumentException("episode must retain its idempotency evidence reference");
        }
        return transaction(connection -> {
            int inserted;
            try (PreparedStatement reserve = connection.prepareStatement("""
                    INSERT INTO coala_episode_idempotency(scope_id, evidence_type, evidence_id, episode_id, payload_sha256, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (scope_id, evidence_type, evidence_id) DO NOTHING
                    """)) {
                reserve.setString(1, episode.scopeId().value());
                reserve.setString(2, idempotencyReference.evidenceType());
                reserve.setString(3, idempotencyReference.evidenceId());
                reserve.setString(4, episode.id().value());
                reserve.setString(5, EpisodePayloadFingerprint.sha256(episode));
                reserve.setTimestamp(6, Timestamp.from(episode.recordedAt()));
                inserted = reserve.executeUpdate();
            }
            if (inserted == 1) {
                insertEpisode(connection, episode);
                return episode;
            }
            String existingId;
            String existingFingerprint;
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT episode_id, payload_sha256 FROM coala_episode_idempotency
                     WHERE scope_id = ? AND evidence_type = ? AND evidence_id = ?
                    """)) {
                query.setString(1, episode.scopeId().value());
                query.setString(2, idempotencyReference.evidenceType());
                query.setString(3, idempotencyReference.evidenceId());
                try (ResultSet row = query.executeQuery()) {
                    if (!row.next()) throw new SQLException("idempotency reservation disappeared after conflict");
                    existingId = row.getString(1);
                    existingFingerprint = row.getString(2).trim();
                }
            }
            if (!existingFingerprint.equals(EpisodePayloadFingerprint.sha256(episode))) {
                throw new MemoryConflictException("episode idempotency reference was reused with a different outcome payload");
            }
            return load(connection, new EpisodeId(existingId))
                    .orElseThrow(() -> new SQLException("idempotency reservation references a missing episode"));
        });
    }

    @Override public Optional<Episode> findById(EpisodeId id) {
        Objects.requireNonNull(id, "id");
        return read(connection -> load(connection, id));
    }

    @Override public Episode compareAndSet(EpisodeId id, long expectedVersion, Episode replacement) {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(replacement, "replacement");
        if (expectedVersion < 0 || !replacement.id().equals(id) || replacement.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("replacement must preserve identity and increment version exactly once");
        }
        return transaction(connection -> {
            try (PreparedStatement current = connection.prepareStatement(
                    "SELECT scope_id, version FROM coala_episode WHERE episode_id = ? FOR UPDATE")) {
                current.setString(1, id.value());
                try (ResultSet row = current.executeQuery()) {
                    if (!row.next()) throw new MemoryNotFoundException("Episode not found: " + id.value());
                    if (!row.getString(1).equals(replacement.scopeId().value())) {
                        throw new IllegalArgumentException("replacement must preserve episode scope");
                    }
                    if (row.getLong(2) != expectedVersion) {
                        throw new MemoryVersionConflictException("Episode version changed from " + expectedVersion + " to " + row.getLong(2));
                    }
                }
            }
            int updated;
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE coala_episode SET
                        scope_id = ?, version = ?, title = ?, summary = ?, outcome = ?, status = ?,
                        started_at = ?, completed_at = ?, recorded_at = ?, retention_until = ?,
                        provenance_source = ?, provenance_source_id = ?, provenance_observed_at = ?, provenance_source_version = ?,
                        reviewed_by = ?, reviewed_at = ?, review_evidence_type = ?, review_evidence_id = ?, superseded_at = ?
                    WHERE episode_id = ? AND version = ?
                    """)) {
                bindEpisodeUpdate(statement, replacement);
                statement.setString(20, id.value()); statement.setLong(21, expectedVersion);
                updated = statement.executeUpdate();
            }
            if (updated != 1) throw conflictForMissingOrVersion(connection, id, expectedVersion);
            deleteEpisodeChildren(connection, id.value());
            insertEpisodeChildren(connection, replacement);
            return replacement;
        });
    }

    @Override public List<Episode> findVerified(EpisodeQuery query) {
        Objects.requireNonNull(query, "query");
        return read(connection -> {
            List<EpisodeId> ids = new ArrayList<>();
            String sql = """
                    SELECT e.episode_id
                      FROM coala_episode e
                     WHERE e.scope_id = ? AND e.reviewed_at <= ?
                       AND (e.status = 'VERIFIED' OR (e.status = 'SUPERSEDED' AND e.superseded_at > ?))
                       AND (? IS NULL OR EXISTS (
                            SELECT 1 FROM coala_episode_entity x
                             WHERE x.episode_id = e.episode_id AND x.entity_type = ? AND x.entity_id = ?))
                     ORDER BY e.completed_at DESC, e.episode_id
                     LIMIT ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, query.scopeId().value());
                statement.setTimestamp(2, Timestamp.from(query.knownAt()));
                statement.setTimestamp(3, Timestamp.from(query.knownAt()));
                if (query.entity() == null) {
                    statement.setNull(4, Types.VARCHAR); statement.setNull(5, Types.VARCHAR); statement.setNull(6, Types.VARCHAR);
                } else {
                    statement.setString(4, query.entity().entityType()); statement.setString(5, query.entity().entityType());
                    statement.setString(6, query.entity().entityId());
                }
                statement.setInt(7, query.limit());
                try (ResultSet rows = statement.executeQuery()) { while (rows.next()) ids.add(new EpisodeId(rows.getString(1))); }
            }
            List<Episode> episodes = new ArrayList<>(ids.size());
            for (EpisodeId id : ids) load(connection, id).ifPresent(episodes::add);
            return List.copyOf(episodes);
        });
    }

    @Override public int deleteExpiredUnverified(Instant now) {
        Objects.requireNonNull(now, "now");
        return transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM coala_episode WHERE status = 'RECORDED' AND retention_until <= ?")) {
                statement.setTimestamp(1, Timestamp.from(now));
                return statement.executeUpdate();
            }
        });
    }

    private static void insertEpisode(Connection connection, Episode episode) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO coala_episode(episode_id, scope_id, version, title, summary, outcome, status,
                    started_at, completed_at, recorded_at, retention_until, provenance_source, provenance_source_id,
                    provenance_observed_at, provenance_source_version, reviewed_by, reviewed_at,
                    review_evidence_type, review_evidence_id, superseded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            bindEpisodeInsert(insert, episode);
            insert.executeUpdate();
        }
        insertEpisodeChildren(connection, episode);
    }

    private static void bindEpisodeInsert(PreparedStatement statement, Episode e) throws SQLException {
        statement.setString(1, e.id().value()); statement.setString(2, e.scopeId().value()); statement.setLong(3, e.version());
        statement.setString(4, e.title()); statement.setString(5, e.summary()); statement.setString(6, e.outcome().name());
        statement.setString(7, e.status().name()); statement.setTimestamp(8, Timestamp.from(e.startedAt()));
        statement.setTimestamp(9, Timestamp.from(e.completedAt())); statement.setTimestamp(10, Timestamp.from(e.recordedAt()));
        setTimestamp(statement, 11, e.retentionUntil());
        Provenance p = e.provenance(); statement.setString(12, p.source().name()); statement.setString(13, p.sourceId());
        statement.setTimestamp(14, Timestamp.from(p.observedAt())); statement.setString(15, p.sourceVersion());
        statement.setString(16, e.reviewedBy()); setTimestamp(statement, 17, e.reviewedAt());
        EvidenceReference review = e.reviewEvidence();
        statement.setString(18, review == null ? null : review.evidenceType());
        statement.setString(19, review == null ? null : review.evidenceId()); setTimestamp(statement, 20, e.supersededAt());
    }

    private static void bindEpisodeUpdate(PreparedStatement statement, Episode e) throws SQLException {
        statement.setString(1, e.scopeId().value()); statement.setLong(2, e.version()); statement.setString(3, e.title());
        statement.setString(4, e.summary()); statement.setString(5, e.outcome().name()); statement.setString(6, e.status().name());
        statement.setTimestamp(7, Timestamp.from(e.startedAt())); statement.setTimestamp(8, Timestamp.from(e.completedAt()));
        statement.setTimestamp(9, Timestamp.from(e.recordedAt())); setTimestamp(statement, 10, e.retentionUntil());
        Provenance p = e.provenance(); statement.setString(11, p.source().name()); statement.setString(12, p.sourceId());
        statement.setTimestamp(13, Timestamp.from(p.observedAt())); statement.setString(14, p.sourceVersion());
        statement.setString(15, e.reviewedBy()); setTimestamp(statement, 16, e.reviewedAt());
        EvidenceReference review = e.reviewEvidence(); statement.setString(17, review == null ? null : review.evidenceType());
        statement.setString(18, review == null ? null : review.evidenceId()); setTimestamp(statement, 19, e.supersededAt());
    }

    private static void setTimestamp(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        else statement.setTimestamp(index, Timestamp.from(value));
    }

    private static void insertEpisodeChildren(Connection connection, Episode episode) throws SQLException {
        try (PreparedStatement entity = connection.prepareStatement("""
                INSERT INTO coala_episode_entity(episode_id, entity_type, entity_id, ordinal) VALUES (?, ?, ?, ?)
                """)) {
            int entityOrdinal = 0;
            for (EntityReference reference : episode.entities()) {
                entity.setString(1, episode.id().value()); entity.setString(2, reference.entityType());
                entity.setString(3, reference.entityId()); entity.setInt(4, entityOrdinal++); entity.addBatch();
            }
            entity.executeBatch();
        }
        try (PreparedStatement event = connection.prepareStatement("""
                INSERT INTO coala_episode_event(episode_id, event_index, event_type, description, occurred_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            try (PreparedStatement evidence = connection.prepareStatement("""
                    INSERT INTO coala_episode_event_evidence(episode_id, event_index, evidence_type, evidence_id, ordinal)
                    VALUES (?, ?, ?, ?, ?)
                    """)) {
                for (int i = 0; i < episode.events().size(); i++) {
                    EpisodeEvent item = episode.events().get(i);
                    event.setString(1, episode.id().value()); event.setInt(2, i); event.setString(3, item.eventType());
                    event.setString(4, item.description()); event.setTimestamp(5, Timestamp.from(item.occurredAt()));
                    event.addBatch();
                    int evidenceOrdinal = 0;
                    for (EvidenceReference ref : item.evidence()) {
                        evidence.setString(1, episode.id().value()); evidence.setInt(2, i);
                        evidence.setString(3, ref.evidenceType()); evidence.setString(4, ref.evidenceId());
                        evidence.setInt(5, evidenceOrdinal++); evidence.addBatch();
                    }
                }
                event.executeBatch(); evidence.executeBatch();
            }
        }
        Set<EvidenceReference> eventEvidence = new HashSet<>();
        episode.events().forEach(item -> eventEvidence.addAll(item.evidence()));
        try (PreparedStatement levelEvidence = connection.prepareStatement("""
                INSERT INTO coala_episode_level_evidence(episode_id, evidence_type, evidence_id) VALUES (?, ?, ?)
                """)) {
            for (EvidenceReference reference : episode.evidence()) {
                if (eventEvidence.contains(reference)) continue;
                levelEvidence.setString(1, episode.id().value()); levelEvidence.setString(2, reference.evidenceType());
                levelEvidence.setString(3, reference.evidenceId()); levelEvidence.addBatch();
            }
            levelEvidence.executeBatch();
        }
        try (PreparedStatement aggregate = connection.prepareStatement("""
                INSERT INTO coala_episode_evidence(episode_id, evidence_type, evidence_id, ordinal)
                VALUES (?, ?, ?, ?)
                """)) {
            int evidenceOrdinal = 0;
            for (EvidenceReference reference : episode.evidence()) {
                aggregate.setString(1, episode.id().value()); aggregate.setString(2, reference.evidenceType());
                aggregate.setString(3, reference.evidenceId()); aggregate.setInt(4, evidenceOrdinal++);
                aggregate.addBatch();
            }
            aggregate.executeBatch();
        }
    }

    private static void deleteEpisodeChildren(Connection connection, String id) throws SQLException {
        // Cascades are used by the schema. Deleting parent child collections explicitly keeps update semantics local.
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM coala_episode_entity WHERE episode_id = ?")) {
            delete.setString(1, id); delete.executeUpdate();
        }
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM coala_episode_event WHERE episode_id = ?")) {
            delete.setString(1, id); delete.executeUpdate();
        }
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM coala_episode_level_evidence WHERE episode_id = ?")) {
            delete.setString(1, id); delete.executeUpdate();
        }
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM coala_episode_evidence WHERE episode_id = ?")) {
            delete.setString(1, id); delete.executeUpdate();
        }
    }

    private static Optional<Episode> load(Connection connection, EpisodeId id) throws SQLException {
        String scope; long version; String title; String summary; EpisodeOutcome outcome; EpisodeStatus status;
        Instant started; Instant completed; Instant recorded; Instant retention;
        ProvenanceSource provenanceSource; String provenanceId; Instant provenanceObserved; String provenanceVersion;
        String reviewedBy; Instant reviewedAt; String reviewType; String reviewId; Instant supersededAt;
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT scope_id, version, title, summary, outcome, status, started_at, completed_at, recorded_at,
                       retention_until, provenance_source, provenance_source_id, provenance_observed_at,
                       provenance_source_version, reviewed_by, reviewed_at, review_evidence_type,
                       review_evidence_id, superseded_at
                  FROM coala_episode WHERE episode_id = ?
                """)) {
            query.setString(1, id.value());
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return Optional.empty();
                scope = row.getString(1); version = row.getLong(2); title = row.getString(3); summary = row.getString(4);
                outcome = EpisodeOutcome.valueOf(row.getString(5)); status = EpisodeStatus.valueOf(row.getString(6));
                started = row.getTimestamp(7).toInstant(); completed = row.getTimestamp(8).toInstant();
                recorded = row.getTimestamp(9).toInstant(); retention = instant(row.getTimestamp(10));
                provenanceSource = ProvenanceSource.valueOf(row.getString(11)); provenanceId = row.getString(12);
                provenanceObserved = row.getTimestamp(13).toInstant(); provenanceVersion = row.getString(14);
                reviewedBy = row.getString(15); reviewedAt = instant(row.getTimestamp(16));
                reviewType = row.getString(17); reviewId = row.getString(18); supersededAt = instant(row.getTimestamp(19));
            }
        }
        List<EntityReference> entities = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT entity_type, entity_id FROM coala_episode_entity WHERE episode_id = ? ORDER BY ordinal, entity_type, entity_id")) {
            query.setString(1, id.value());
            try (ResultSet rows = query.executeQuery()) { while (rows.next()) entities.add(new EntityReference(rows.getString(1), rows.getString(2))); }
        }
        List<EpisodeEvent> events = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT event_index, event_type, description, occurred_at FROM coala_episode_event
                 WHERE episode_id = ? ORDER BY event_index
                """)) {
            query.setString(1, id.value());
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    int eventIndex = rows.getInt(1);
                    events.add(new EpisodeEvent(rows.getString(2), rows.getString(3), rows.getTimestamp(4).toInstant(),
                            eventEvidence(connection, id.value(), eventIndex)));
                }
            }
        }
        LinkedHashSet<EvidenceReference> evidence = new LinkedHashSet<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT evidence_type, evidence_id FROM coala_episode_evidence
                 WHERE episode_id = ? ORDER BY ordinal
                """)) {
            query.setString(1, id.value());
            try (ResultSet rows = query.executeQuery()) { while (rows.next()) evidence.add(new EvidenceReference(rows.getString(1), rows.getString(2))); }
        }
        // Compatibility for rows created before V004: reconstruct aggregate evidence from legacy tables.
        if (evidence.isEmpty()) {
            try (PreparedStatement query = connection.prepareStatement("""
                    SELECT evidence_type, evidence_id FROM coala_episode_level_evidence
                     WHERE episode_id = ? ORDER BY evidence_type, evidence_id
                    """)) {
                query.setString(1, id.value());
                try (ResultSet rows = query.executeQuery()) { while (rows.next()) evidence.add(new EvidenceReference(rows.getString(1), rows.getString(2))); }
            }
            events.forEach(event -> evidence.addAll(event.evidence()));
        }
        EvidenceReference reviewEvidence = reviewType == null ? null : new EvidenceReference(reviewType, reviewId);
        Provenance provenance = new Provenance(provenanceSource, provenanceId, provenanceObserved, recorded, provenanceVersion);
        Episode episode = new Episode(id, new MemoryScopeId(scope), version, title, summary, outcome, status,
                started, completed, recorded, retention, entities, events, List.copyOf(evidence), provenance,
                reviewedBy, reviewedAt, reviewEvidence, supersededAt);
        return Optional.of(episode);
    }

    private static List<EvidenceReference> eventEvidence(Connection connection, String episodeId, int eventIndex) throws SQLException {
        List<EvidenceReference> refs = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT evidence_type, evidence_id FROM coala_episode_event_evidence
                 WHERE episode_id = ? AND event_index = ? ORDER BY ordinal, evidence_type, evidence_id
                """)) {
            query.setString(1, episodeId); query.setInt(2, eventIndex);
            try (ResultSet rows = query.executeQuery()) { while (rows.next()) refs.add(new EvidenceReference(rows.getString(1), rows.getString(2))); }
        }
        return List.copyOf(refs);
    }

    private static RuntimeException conflictForMissingOrVersion(Connection connection, EpisodeId id,
                                                                  long expectedVersion) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT version FROM coala_episode WHERE episode_id = ? FOR UPDATE")) {
            query.setString(1, id.value());
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return new MemoryNotFoundException("Episode not found: " + id.value());
                return new MemoryVersionConflictException("Episode version changed from " + expectedVersion + " to " + row.getLong(1));
            }
        }
    }


    @Override protected RuntimeException translate(SQLException ex) {
        if ("23505".equals(ex.getSQLState())) return new MemoryConflictException("Episode already exists or its idempotency reference conflicts");
        return super.translate(ex);
    }
}
