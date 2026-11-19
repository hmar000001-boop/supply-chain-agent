package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;
import dev.supplychain.agent.runtime.cognitive.CognitiveCycleOperations;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ObservationStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/** Immutable observation store requiring trusted principal/source/time provenance from the application host. */
public final class PostgresObservationStore implements ObservationStore {
    private final DataSource dataSource;
    private final String scopeId;
    private final JsonCodec<CognitiveCycleOperations.ExternalObservation> codec;
    private final ObservationProvenanceResolver provenance;

    public PostgresObservationStore(DataSource dataSource, String scopeId,
                                    JsonCodec<CognitiveCycleOperations.ExternalObservation> codec,
                                    ObservationProvenanceResolver provenance) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.scopeId = required(scopeId); this.codec = Objects.requireNonNull(codec, "codec");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
    }
    @Override public void save(CognitiveCycleOperations.ExternalObservation observation) {
        Objects.requireNonNull(observation, "observation");
        var source = Objects.requireNonNull(provenance.resolve(observation), "observation provenance");
        String payload = PostgresJsonArtifactRepository.validJson(codec.encode(observation));
        String fingerprint = PostgresJsonArtifactRepository.sha256(payload);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO external_observation(scope_id,observation_id,execution_id,principal_id,source_reference,
                            disposition,evidence_set_id,observed_at,payload_fingerprint,payload)
                        VALUES(?,?,?,?,?,?,?,?,?,CAST(? AS jsonb))
                        ON CONFLICT(scope_id,observation_id) DO NOTHING
                        """)) {
                    insert.setString(1, scopeId); insert.setString(2, observation.observationId());
                    insert.setString(3, source.executionId()); insert.setString(4, source.principalId());
                    insert.setString(5, source.sourceReference()); insert.setString(6, observation.disposition().name());
                    insert.setString(7, observation.evidenceSetId().isBlank() ? null : observation.evidenceSetId());
                    insert.setObject(8, source.observedAt()); insert.setString(9, fingerprint); insert.setString(10, payload);
                    insert.executeUpdate();
                }
                try (PreparedStatement verify = connection.prepareStatement("""
                        SELECT execution_id,principal_id,source_reference,disposition,evidence_set_id,observed_at,payload_fingerprint
                        FROM external_observation WHERE scope_id=? AND observation_id=? FOR UPDATE
                        """)) {
                    verify.setString(1, scopeId); verify.setString(2, observation.observationId());
                    try (ResultSet row = verify.executeQuery()) {
                        if (!row.next() || !source.executionId().equals(row.getString(1))
                                || !source.principalId().equals(row.getString(2))
                                || !source.sourceReference().equals(row.getString(3))
                                || !observation.disposition().name().equals(row.getString(4))
                                || !Objects.equals(observation.evidenceSetId().isBlank() ? null : observation.evidenceSetId(), row.getString(5))
                                || !source.observedAt().equals(row.getTimestamp(6).toInstant())
                                || !fingerprint.equals(row.getString(7).trim())) {
                            throw new SecurityException("observation ID was reused with different provenance or content");
                        }
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        } catch (SQLException ex) { throw new PostgresPersistenceException("PostgreSQL observation write failed", ex); }
    }
    @Override public Optional<CognitiveCycleOperations.ExternalObservation> find(String observationId) {
        String id = required(observationId);
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT payload::text,payload_fingerprint FROM external_observation WHERE scope_id=? AND observation_id=?")) {
            statement.setString(1, scopeId); statement.setString(2, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                String json = rows.getString(1);
                CognitiveCycleOperations.ExternalObservation observation = codec.decode(json);
                String canonical = PostgresJsonArtifactRepository.validJson(codec.encode(observation));
                if (!PostgresJsonArtifactRepository.sha256(canonical).equals(rows.getString(2).trim())) {
                    throw new SecurityException("persisted observation payload fingerprint mismatch");
                }
                if (!id.equals(observation.observationId())) throw new SecurityException("observation payload identity mismatch");
                return Optional.of(observation);
            }
        } catch (SQLException ex) { throw new PostgresPersistenceException("PostgreSQL observation read failed", ex); }
    }
    private static String required(String value) { String result=Objects.requireNonNull(value,"identity").trim(); if(result.isEmpty())throw new IllegalArgumentException("identity cannot be blank"); return result; }
}
