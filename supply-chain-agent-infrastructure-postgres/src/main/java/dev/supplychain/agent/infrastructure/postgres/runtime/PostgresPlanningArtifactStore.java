package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.planning.session.PlannerSessionCheckpoint;
import dev.supplychain.agent.planning.session.PlannerSessionStore;
import dev.supplychain.agent.runtime.cognitive.decision.PlanningArtifactStore;

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

/** Durable, versioned planning input/result bundle bound to its execution, memory and planner session. */
public final class PostgresPlanningArtifactStore implements PlanningArtifactStore {
    private final DataSource dataSource;
    private final String scopeId;
    private final JsonCodec<PlanningArtifacts> codec;
    private final PlannerSessionStore sessions;

    public PostgresPlanningArtifactStore(DataSource dataSource, String scopeId,
                                        JsonCodec<PlanningArtifacts> codec, PlannerSessionStore sessions) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.scopeId = required(scopeId, "scopeId");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @Override public String store(dev.supplychain.agent.planning.api.PlanningRequest request,
                                  dev.supplychain.agent.planning.api.PlanningResult result, Instant storedAt) {
        Objects.requireNonNull(request, "request"); Objects.requireNonNull(result, "result"); Objects.requireNonNull(storedAt, "storedAt");
        if (!scopeId.equals(request.workingMemory().scopeId().value())) {
            throw new SecurityException("planning artifact working memory belongs to another persistence scope");
        }
        if (request.resumeSessionId() != null && !request.resumeSessionId().equals(result.sessionId())) {
            throw new SecurityException("planner result session differs from the requested resume session");
        }
        Optional<PlannerSessionCheckpoint> checkpoint = sessions.find(result.sessionId());
        if (checkpoint.isPresent()) {
            PlannerSessionCheckpoint saved = checkpoint.get();
            if (saved.plannerId() != result.plannerId() || !saved.requestFingerprint().equals(request.fingerprint())) {
                throw new SecurityException("planner session ownership or request fingerprint does not match planning artifact");
            }
        } else if (request.resumeSessionId() != null) {
            throw new SecurityException("resumed planning session is missing from the durable planner-session store");
        }
        String candidateSetId = candidateSetId(request, result);
        PlanningArtifacts artifacts = new PlanningArtifacts(candidateSetId, request.executionId(), request, result, storedAt);
        String payload = PostgresJsonArtifactRepository.validJson(codec.encode(artifacts));
        String payloadFingerprint = PostgresJsonArtifactRepository.sha256(payload);
        String configFingerprint = checkpoint.map(PlannerSessionCheckpoint::algorithmConfigurationFingerprint).orElse(null);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO planning_artifact(scope_id,candidate_set_id,execution_id,request_fingerprint,planner_id,
                            planner_session_id,algorithm_config_fingerprint,working_memory_id,working_memory_version,
                            payload_fingerprint,payload,created_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?)
                        ON CONFLICT(scope_id,candidate_set_id) DO NOTHING
                        """)) {
                    insert.setString(1, scopeId); insert.setString(2, candidateSetId); insert.setString(3, request.executionId());
                    insert.setString(4, request.fingerprint()); insert.setString(5, result.plannerId().name());
                    insert.setString(6, result.sessionId()); insert.setString(7, configFingerprint);
                    insert.setString(8, request.workingMemory().id().value()); insert.setLong(9, request.workingMemory().version());
                    insert.setString(10, payloadFingerprint); insert.setString(11, payload); PostgresJdbc.setInstant(insert, 12, storedAt);
                    insert.executeUpdate();
                }
                try (PreparedStatement select = connection.prepareStatement("""
                        SELECT execution_id,request_fingerprint,planner_id,planner_session_id,algorithm_config_fingerprint,
                               working_memory_id,working_memory_version,payload_fingerprint
                        FROM planning_artifact WHERE scope_id=? AND candidate_set_id=? FOR UPDATE
                        """)) {
                    select.setString(1, scopeId); select.setString(2, candidateSetId);
                    try (ResultSet row = select.executeQuery()) {
                        if (!row.next() || !request.executionId().equals(row.getString(1))
                                || !request.fingerprint().equals(row.getString(2).trim())
                                || !result.plannerId().name().equals(row.getString(3))
                                || !result.sessionId().equals(row.getString(4))
                                || !Objects.equals(configFingerprint, row.getString(5))
                                || !request.workingMemory().id().value().equals(row.getString(6))
                                || request.workingMemory().version() != row.getLong(7)
                                || !payloadFingerprint.equals(row.getString(8).trim())) {
                            throw new SecurityException("candidate-set identity was reused for a different request, session, memory version or result");
                        }
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("PostgreSQL planning artifact write failed", ex);
        }
        return candidateSetId;
    }

    @Override public Optional<PlanningArtifacts> find(String candidateSetId) {
        String id = required(candidateSetId, "candidateSetId");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT execution_id,request_fingerprint,planner_id,planner_session_id,working_memory_id,
                            working_memory_version,payload_fingerprint,payload::text
                     FROM planning_artifact WHERE scope_id=? AND candidate_set_id=?
                     """)) {
            statement.setString(1, scopeId); statement.setString(2, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                PlanningArtifacts value = codec.decode(rows.getString(8));
                if (!id.equals(value.candidateSetId())) {
                    throw new SecurityException("stored planning artifact candidate_set_id does not match its decoded payload");
                }
                if (!rows.getString(1).equals(value.executionId())) {
                    throw new SecurityException("stored planning artifact execution_id does not match its decoded payload");
                }
                if (!rows.getString(2).trim().equals(value.request().fingerprint())) {
                    throw new SecurityException("stored planning artifact request_fingerprint does not match its decoded payload");
                }
                if (!rows.getString(3).equals(value.result().plannerId().name())) {
                    throw new SecurityException("stored planning artifact planner_id does not match its decoded payload");
                }
                if (!rows.getString(4).equals(value.result().sessionId())) {
                    throw new SecurityException("stored planning artifact planner_session_id does not match its decoded payload");
                }
                if (!rows.getString(5).equals(value.request().workingMemory().id().value())) {
                    throw new SecurityException("stored planning artifact working_memory_id does not match its decoded payload");
                }
                if (rows.getLong(6) != value.request().workingMemory().version()) {
                    throw new SecurityException("stored planning artifact working_memory_version does not match its decoded payload");
                }
                String reencodedPayload = PostgresJsonArtifactRepository.validJson(codec.encode(value));
                if (!rows.getString(7).trim().equals(PostgresJsonArtifactRepository.sha256(reencodedPayload))) {
                    throw new SecurityException("stored planning artifact payload_fingerprint does not match its re-encoded payload");
                }
                PlannerSessionCheckpoint checkpoint = sessions.find(value.result().sessionId()).orElse(null);
                if (checkpoint != null && (checkpoint.plannerId() != value.result().plannerId()
                        || !checkpoint.requestFingerprint().equals(value.request().fingerprint()))) {
                    throw new SecurityException("stored planner session no longer matches the planning artifact");
                }
                return Optional.of(value);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("PostgreSQL planning artifact read failed", ex);
        }
    }

    private static String candidateSetId(dev.supplychain.agent.planning.api.PlanningRequest request,
                                         dev.supplychain.agent.planning.api.PlanningResult result) {
        String canonical = request.executionId() + "|" + request.fingerprint() + "|" + result.plannerId() + "|"
                + result.sessionId() + "|" + result.terminationReason() + "|" + result.statistics() + "|"
                + result.candidates() + "|" + result.trace();
        try {
            return "candidates:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank"); return result;
    }
}
