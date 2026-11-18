package dev.supplychain.agent.infrastructure.postgres.memory;

import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryConflictException;
import dev.supplychain.agent.memory.common.MemoryNotFoundException;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.MemoryVersionConflictException;
import dev.supplychain.agent.memory.working.Hypothesis;
import dev.supplychain.agent.memory.working.HypothesisId;
import dev.supplychain.agent.memory.working.HypothesisStatus;
import dev.supplychain.agent.memory.working.MemoryValue;
import dev.supplychain.agent.memory.working.WorkingMemoryId;
import dev.supplychain.agent.memory.working.WorkingMemoryRepository;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Durable JDBC implementation of M2 working memory with transactional snapshot replacement and CAS. */
public final class PostgresWorkingMemoryRepository extends JdbcRepositorySupport implements WorkingMemoryRepository {
    public PostgresWorkingMemoryRepository(DataSource dataSource) { super(dataSource); }

    @Override public WorkingMemorySnapshot create(WorkingMemorySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return transaction(connection -> {
            insertHeader(connection, snapshot);
            replaceChildren(connection, snapshot);
            return snapshot;
        });
    }

    @Override public Optional<WorkingMemorySnapshot> findById(WorkingMemoryId id) {
        Objects.requireNonNull(id, "id");
        return read(connection -> load(connection, id));
    }

    @Override public WorkingMemorySnapshot compareAndSet(WorkingMemoryId id, long expectedVersion,
                                                          WorkingMemorySnapshot replacement) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(replacement, "replacement");
        if (expectedVersion < 0 || !replacement.id().equals(id) || replacement.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("replacement must preserve identity and increment version exactly once");
        }
        return transaction(connection -> {
            try (PreparedStatement current = connection.prepareStatement(
                    "SELECT scope_id, version FROM coala_working_memory WHERE memory_id = ? FOR UPDATE")) {
                current.setString(1, id.value());
                try (ResultSet row = current.executeQuery()) {
                    if (!row.next()) throw new MemoryNotFoundException("Working memory not found: " + id.value());
                    if (!row.getString(1).equals(replacement.scopeId().value())) {
                        throw new IllegalArgumentException("replacement must preserve working-memory scope");
                    }
                    if (row.getLong(2) != expectedVersion) {
                        throw new MemoryVersionConflictException("Working memory version changed from " + expectedVersion + " to " + row.getLong(2));
                    }
                }
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE coala_working_memory
                       SET version = ?, updated_at = ?, expires_at = ?
                     WHERE memory_id = ? AND version = ?
                    """)) {
                update.setLong(1, replacement.version());
                update.setTimestamp(2, Timestamp.from(replacement.updatedAt()));
                update.setTimestamp(3, Timestamp.from(replacement.expiresAt()));
                update.setString(4, id.value());
                update.setLong(5, expectedVersion);
                if (update.executeUpdate() != 1) throw conflictForMissingOrVersion(connection, id, expectedVersion);
            }
            replaceChildren(connection, replacement);
            return replacement;
        });
    }

    @Override public int deleteExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        return transaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM coala_working_memory WHERE expires_at <= ?")) {
                delete.setTimestamp(1, Timestamp.from(now));
                return delete.executeUpdate();
            }
        });
    }

    private static void insertHeader(Connection connection, WorkingMemorySnapshot snapshot) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO coala_working_memory(memory_id, scope_id, version, created_at, updated_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, snapshot.id().value());
            insert.setString(2, snapshot.scopeId().value());
            insert.setLong(3, snapshot.version());
            insert.setTimestamp(4, Timestamp.from(snapshot.createdAt()));
            insert.setTimestamp(5, Timestamp.from(snapshot.updatedAt()));
            insert.setTimestamp(6, Timestamp.from(snapshot.expiresAt()));
            insert.executeUpdate();
        }
    }

    private static void replaceChildren(Connection connection, WorkingMemorySnapshot snapshot) throws SQLException {
        try (PreparedStatement deleteSlots = connection.prepareStatement(
                    "DELETE FROM coala_working_memory_slot WHERE memory_id = ?");
             PreparedStatement deleteHypotheses = connection.prepareStatement(
                    "DELETE FROM coala_working_memory_hypothesis WHERE memory_id = ?")) {
            deleteSlots.setString(1, snapshot.id().value());
            deleteSlots.executeUpdate();
            deleteHypotheses.setString(1, snapshot.id().value());
            deleteHypotheses.executeUpdate();
        }
        int ordinal = 0;
        for (Map.Entry<String, dev.supplychain.agent.memory.working.WorkingMemoryEntry> entry : snapshot.values().entrySet()) {
            insertSlot(connection, snapshot.id().value(), entry.getKey(), entry.getValue(), ordinal++);
        }
        ordinal = 0;
        for (Hypothesis hypothesis : snapshot.hypotheses().values()) {
            insertHypothesis(connection, snapshot.id().value(), hypothesis, ordinal++);
        }
    }

    private static void insertSlot(Connection connection, String memoryId, String key,
                                   dev.supplychain.agent.memory.working.WorkingMemoryEntry entry, int ordinal) throws SQLException {
        String type;
        String text = null;
        BigDecimal decimal = null;
        Boolean flag = null;
        Instant timestamp = null;
        String entityType = null;
        String entityId = null;
        String textList = null;
        MemoryValue value = entry.value();
        if (value instanceof MemoryValue.Text v) { type = "TEXT"; text = v.value(); }
        else if (value instanceof MemoryValue.Decimal v) { type = "DECIMAL"; decimal = v.value(); }
        else if (value instanceof MemoryValue.Flag v) { type = "FLAG"; flag = v.value(); }
        else if (value instanceof MemoryValue.Timestamp v) { type = "TIMESTAMP"; timestamp = v.value(); }
        else if (value instanceof MemoryValue.EntityLink v) {
            type = "ENTITY_LINK"; entityType = v.value().entityType(); entityId = v.value().entityId();
        } else if (value instanceof MemoryValue.TextList v) {
            type = "TEXT_LIST"; textList = JsonTextList.encode(v.values());
        } else throw new IllegalArgumentException("unsupported working-memory value type: " + value.getClass());

        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO coala_working_memory_slot(memory_id, slot_key, value_type, text_value, decimal_value,
                    flag_value, timestamp_value, entity_type, entity_id, text_list_value, updated_at, ordinal)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """)) {
            insert.setString(1, memoryId); insert.setString(2, key); insert.setString(3, type);
            insert.setString(4, text);
            if (decimal == null) insert.setNull(5, Types.NUMERIC); else insert.setBigDecimal(5, decimal);
            if (flag == null) insert.setNull(6, Types.BOOLEAN); else insert.setBoolean(6, flag);
            if (timestamp == null) insert.setNull(7, Types.TIMESTAMP_WITH_TIMEZONE); else insert.setTimestamp(7, Timestamp.from(timestamp));
            insert.setString(8, entityType); insert.setString(9, entityId); insert.setString(10, textList);
            insert.setTimestamp(11, Timestamp.from(entry.updatedAt()));
            insert.setInt(12, ordinal);
            insert.executeUpdate();
        }
        try (PreparedStatement evidence = connection.prepareStatement("""
                INSERT INTO coala_working_memory_slot_evidence(memory_id, slot_key, evidence_type, evidence_id, ordinal)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            int evidenceOrdinal = 0;
            for (EvidenceReference reference : entry.evidence()) {
                evidence.setString(1, memoryId); evidence.setString(2, key);
                evidence.setString(3, reference.evidenceType()); evidence.setString(4, reference.evidenceId());
                evidence.setInt(5, evidenceOrdinal++); evidence.addBatch();
            }
            evidence.executeBatch();
        }
    }

    private static void insertHypothesis(Connection connection, String memoryId, Hypothesis hypothesis, int ordinal) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO coala_working_memory_hypothesis(memory_id, hypothesis_id, statement, status, confidence, created_at, updated_at, ordinal)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, memoryId); insert.setString(2, hypothesis.id().value());
            insert.setString(3, hypothesis.statement()); insert.setString(4, hypothesis.status().name());
            insert.setDouble(5, hypothesis.confidence()); insert.setTimestamp(6, Timestamp.from(hypothesis.createdAt()));
            insert.setTimestamp(7, Timestamp.from(hypothesis.updatedAt())); insert.setInt(8, ordinal); insert.executeUpdate();
        }
        try (PreparedStatement evidence = connection.prepareStatement("""
                INSERT INTO coala_working_memory_hypothesis_evidence(memory_id, hypothesis_id, evidence_role, evidence_type, evidence_id, ordinal)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            addHypothesisEvidence(evidence, memoryId, hypothesis.id().value(), "SUPPORTS", hypothesis.supportingEvidence());
            addHypothesisEvidence(evidence, memoryId, hypothesis.id().value(), "CONTRADICTS", hypothesis.contradictingEvidence());
            evidence.executeBatch();
        }
    }

    private static void addHypothesisEvidence(PreparedStatement statement, String memoryId, String hypothesisId,
                                              String role, List<EvidenceReference> references) throws SQLException {
        int ordinal = 0;
        for (EvidenceReference reference : references) {
            statement.setString(1, memoryId); statement.setString(2, hypothesisId); statement.setString(3, role);
            statement.setString(4, reference.evidenceType()); statement.setString(5, reference.evidenceId());
            statement.setInt(6, ordinal++); statement.addBatch();
        }
    }

    private static Optional<WorkingMemorySnapshot> load(Connection connection, WorkingMemoryId id) throws SQLException {
        String scope;
        long version;
        Instant createdAt;
        Instant updatedAt;
        Instant expiresAt;
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT scope_id, version, created_at, updated_at, expires_at
                  FROM coala_working_memory WHERE memory_id = ?
                """)) {
            query.setString(1, id.value());
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) return Optional.empty();
                scope = result.getString(1); version = result.getLong(2);
                createdAt = instant(result.getTimestamp(3)); updatedAt = instant(result.getTimestamp(4));
                expiresAt = instant(result.getTimestamp(5));
            }
        }
        LinkedHashMap<String, dev.supplychain.agent.memory.working.WorkingMemoryEntry> values = new LinkedHashMap<>();
        List<SlotRow> slotRows = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT slot_key, value_type, text_value, decimal_value, flag_value, timestamp_value,
                       entity_type, entity_id, text_list_value::text, updated_at
                  FROM coala_working_memory_slot WHERE memory_id = ? ORDER BY ordinal, slot_key
                """)) {
            query.setString(1, id.value());
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) slotRows.add(new SlotRow(result.getString(1), result.getString(2),
                        result.getString(3), result.getBigDecimal(4), (Boolean) result.getObject(5),
                        instant(result.getTimestamp(6)), result.getString(7), result.getString(8),
                        result.getString(9), instant(result.getTimestamp(10))));
            }
        }
        for (SlotRow row : slotRows) {
            MemoryValue value = row.toMemoryValue();
            values.put(row.key(), new dev.supplychain.agent.memory.working.WorkingMemoryEntry(
                    value, slotEvidence(connection, id.value(), row.key()), row.updatedAt()));
        }
        LinkedHashMap<HypothesisId, Hypothesis> hypotheses = new LinkedHashMap<>();
        List<HypothesisRow> hypothesisRows = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT hypothesis_id, statement, status, confidence, created_at, updated_at
                  FROM coala_working_memory_hypothesis WHERE memory_id = ? ORDER BY ordinal, hypothesis_id
                """)) {
            query.setString(1, id.value());
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) hypothesisRows.add(new HypothesisRow(result.getString(1), result.getString(2),
                        result.getString(3), result.getDouble(4), instant(result.getTimestamp(5)), instant(result.getTimestamp(6))));
            }
        }
        for (HypothesisRow row : hypothesisRows) {
            HypothesisId hypothesisId = new HypothesisId(row.id());
            List<EvidenceReference> supporting = hypothesisEvidence(connection, id.value(), hypothesisId.value(), "SUPPORTS");
            List<EvidenceReference> contradicting = hypothesisEvidence(connection, id.value(), hypothesisId.value(), "CONTRADICTS");
            Hypothesis hypothesis = new Hypothesis(hypothesisId, row.statement(), HypothesisStatus.valueOf(row.status()),
                    row.confidence(), supporting, contradicting, row.createdAt(), row.updatedAt());
            hypotheses.put(hypothesisId, hypothesis);
        }
        List<EvidenceReference> allEvidence = collectEvidence(values, hypotheses);
        return Optional.of(new WorkingMemorySnapshot(id, new MemoryScopeId(scope), version, createdAt,
                updatedAt, expiresAt, values, hypotheses, allEvidence));
    }

    private record SlotRow(String key, String type, String text, BigDecimal decimal, Boolean flag,
                           Instant timestamp, String entityType, String entityId, String textList, Instant updatedAt) {
        MemoryValue toMemoryValue() {
            return switch (type) {
                case "TEXT" -> new MemoryValue.Text(text);
                case "DECIMAL" -> new MemoryValue.Decimal(decimal);
                case "FLAG" -> new MemoryValue.Flag(Boolean.TRUE.equals(flag));
                case "TIMESTAMP" -> new MemoryValue.Timestamp(timestamp);
                case "ENTITY_LINK" -> new MemoryValue.EntityLink(new EntityReference(entityType, entityId));
                case "TEXT_LIST" -> new MemoryValue.TextList(JsonTextList.decode(textList));
                default -> throw new IllegalStateException("unknown persisted memory value type: " + type);
            };
        }
    }
    private record HypothesisRow(String id, String statement, String status, double confidence,
                                 Instant createdAt, Instant updatedAt) { }

    private static List<EvidenceReference> slotEvidence(Connection connection, String memoryId, String key) throws SQLException {
        List<EvidenceReference> result = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT evidence_type, evidence_id FROM coala_working_memory_slot_evidence
                 WHERE memory_id = ? AND slot_key = ? ORDER BY ordinal, evidence_type, evidence_id
                """)) {
            query.setString(1, memoryId); query.setString(2, key);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.add(new EvidenceReference(rows.getString(1), rows.getString(2)));
            }
        }
        return List.copyOf(result);
    }

    private static List<EvidenceReference> hypothesisEvidence(Connection connection, String memoryId,
                                                                String hypothesisId, String role) throws SQLException {
        List<EvidenceReference> result = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT evidence_type, evidence_id FROM coala_working_memory_hypothesis_evidence
                 WHERE memory_id = ? AND hypothesis_id = ? AND evidence_role = ? ORDER BY ordinal, evidence_type, evidence_id
                """)) {
            query.setString(1, memoryId); query.setString(2, hypothesisId); query.setString(3, role);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.add(new EvidenceReference(rows.getString(1), rows.getString(2)));
            }
        }
        return List.copyOf(result);
    }

    private static List<EvidenceReference> collectEvidence(
            Map<String, dev.supplychain.agent.memory.working.WorkingMemoryEntry> values,
            Map<HypothesisId, Hypothesis> hypotheses) {
        LinkedHashSet<EvidenceReference> evidence = new LinkedHashSet<>();
        values.values().forEach(entry -> evidence.addAll(entry.evidence()));
        hypotheses.values().forEach(h -> { evidence.addAll(h.supportingEvidence()); evidence.addAll(h.contradictingEvidence()); });
        return List.copyOf(evidence);
    }

    private static RuntimeException conflictForMissingOrVersion(Connection connection, WorkingMemoryId id,
                                                                  long expectedVersion) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT version FROM coala_working_memory WHERE memory_id = ? FOR UPDATE")) {
            query.setString(1, id.value());
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return new MemoryNotFoundException("Working memory not found: " + id.value());
                return new MemoryVersionConflictException("Working memory version changed from " + expectedVersion + " to " + row.getLong(1));
            }
        }
    }

    @Override protected RuntimeException translate(SQLException ex) {
        if ("23505".equals(ex.getSQLState())) return new MemoryConflictException("Working memory already exists or violates a uniqueness invariant");
        return super.translate(ex);
    }
}
