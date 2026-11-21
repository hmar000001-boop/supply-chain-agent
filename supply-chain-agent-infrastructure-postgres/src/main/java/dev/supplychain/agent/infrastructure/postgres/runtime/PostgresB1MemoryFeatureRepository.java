package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;
import dev.supplychain.agent.mechanisms.b1.B1MemoryCandidateReference;
import dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureRepository;
import dev.supplychain.agent.mechanisms.b1.B1MemoryFeatureState;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/** Scope-bound PostgreSQL CAS adapter for B1 access, cue-association and outcome utility features. */
public final class PostgresB1MemoryFeatureRepository implements B1MemoryFeatureRepository {
    private final DataSource dataSource;
    private final JsonCodec<B1MemoryFeatureState> codec;
    private final String scopeId;

    public PostgresB1MemoryFeatureRepository(DataSource dataSource, String scopeId,
            JsonCodec<B1MemoryFeatureState> codec) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.scopeId = required(scopeId);
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override public Optional<B1MemoryFeatureState> find(B1MemoryCandidateReference reference) {
        requireScope(reference);
        try (Connection c = dataSource.getConnection(); PreparedStatement q = c.prepareStatement("""
                SELECT payload::text,revision FROM b1_memory_feature_state
                WHERE scope_id=? AND memory_kind=? AND memory_item_id=? AND memory_item_version=?
                """)) {
            bindKey(q, reference);
            try (ResultSet r = q.executeQuery()) {
                if (!r.next()) return Optional.empty();
                B1MemoryFeatureState state = codec.decode(r.getString(1));
                if (state.revision() != r.getLong(2) || !state.reference().equals(reference))
                    throw new SecurityException("B1 feature payload/header mismatch");
                return Optional.of(state);
            }
        } catch (SQLException e) { throw new PostgresPersistenceException("PostgreSQL B1 feature lookup failed", e); }
    }

    @Override public void saveNew(B1MemoryFeatureState state) {
        requireScope(state.reference());
        if (state.revision() != 1) throw new IllegalArgumentException("new B1 feature state must begin at revision 1");
        String payload = PostgresJsonArtifactRepository.validJson(codec.encode(state));
        try (Connection c = dataSource.getConnection(); PreparedStatement insert = c.prepareStatement("""
                INSERT INTO b1_memory_feature_state(scope_id,memory_kind,memory_item_id,memory_item_version,revision,payload,updated_at)
                VALUES(?,?,?,?,?,CAST(? AS jsonb),?)
                """)) {
            bindKey(insert, state.reference()); insert.setLong(5, state.revision()); insert.setString(6, payload);
            PostgresJdbc.setInstant(insert, 7, state.updatedAt()); insert.executeUpdate();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) throw new IllegalStateException("B1 feature state already exists", e);
            throw new PostgresPersistenceException("PostgreSQL B1 feature creation failed", e);
        }
    }

    @Override public boolean compareAndSet(B1MemoryCandidateReference reference, long expectedRevision,
            B1MemoryFeatureState replacement) {
        requireScope(reference);
        if (!reference.equals(replacement.reference()) || replacement.revision() != expectedRevision + 1)
            throw new IllegalArgumentException("B1 feature CAS must preserve key and increment revision exactly once");
        String payload = PostgresJsonArtifactRepository.validJson(codec.encode(replacement));
        try (Connection c = dataSource.getConnection(); PreparedStatement update = c.prepareStatement("""
                UPDATE b1_memory_feature_state SET revision=?,payload=CAST(? AS jsonb),updated_at=?
                WHERE scope_id=? AND memory_kind=? AND memory_item_id=? AND memory_item_version=? AND revision=?
                """)) {
            update.setLong(1, replacement.revision()); update.setString(2, payload);
            PostgresJdbc.setInstant(update, 3, replacement.updatedAt());
            update.setString(4, scopeId); update.setString(5, reference.kind().name());
            update.setString(6, reference.itemId()); update.setLong(7, reference.itemVersion());
            update.setLong(8, expectedRevision); return update.executeUpdate() == 1;
        } catch (SQLException e) { throw new PostgresPersistenceException("PostgreSQL B1 feature CAS failed", e); }
    }

    private void requireScope(B1MemoryCandidateReference reference) {
        if (!scopeId.equals(Objects.requireNonNull(reference, "reference").scopeId().value()))
            throw new SecurityException("B1 feature scope mismatch");
    }
    private static void bindKey(PreparedStatement q, B1MemoryCandidateReference reference) throws SQLException {
        q.setString(1, reference.scopeId().value()); q.setString(2, reference.kind().name());
        q.setString(3, reference.itemId()); q.setLong(4, reference.itemVersion());
    }
    private static String required(String value) {
        String result = Objects.requireNonNull(value, "scopeId").trim();
        if (result.isEmpty()) throw new IllegalArgumentException("scopeId cannot be blank");
        return result;
    }
}
