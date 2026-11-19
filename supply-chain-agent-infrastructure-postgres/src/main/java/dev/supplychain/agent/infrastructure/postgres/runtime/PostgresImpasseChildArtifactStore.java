package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.mechanisms.b2.ChildResolutionResult;
import dev.supplychain.agent.mechanisms.b4.DelegationReturn;
import dev.supplychain.agent.runtime.cognitive.lifecycle.ImpasseChildArtifactStore;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/** Immutable B2 child return artifact; parent objective/capabilities remain outside the child payload. */
public final class PostgresImpasseChildArtifactStore implements ImpasseChildArtifactStore {
    private final PostgresJsonArtifactRepository<ImpasseChildArtifact> artifacts;
    private final Clock clock;
    public PostgresImpasseChildArtifactStore(DataSource dataSource, String scopeId,
                                             JsonCodec<ImpasseChildArtifact> codec) {
        this(dataSource, scopeId, codec, Clock.systemUTC());
    }
    public PostgresImpasseChildArtifactStore(DataSource dataSource, String scopeId,
                                             JsonCodec<ImpasseChildArtifact> codec, Clock clock) {
        artifacts = new PostgresJsonArtifactRepository<>(dataSource, scopeId, "B2_CHILD_ARTIFACT", codec);
        this.clock = Objects.requireNonNull(clock, "clock");
    }
    @Override public void save(String caseId, ChildResolutionResult result, DelegationReturn intentionReturn) {
        Objects.requireNonNull(result, "result"); Objects.requireNonNull(intentionReturn, "intentionReturn");
        if (!required(caseId).equals(result.caseId())) throw new SecurityException("B2 result case identity mismatch");
        artifacts.save(caseId, result.parentRequestId(), result.objectiveFingerprint(),
                result.parentWorkingMemoryVersion(), new ImpasseChildArtifact(result, intentionReturn), clock.instant());
    }
    @Override public Optional<ImpasseChildArtifact> find(String caseId) {
        return artifacts.find(required(caseId)).map(value -> {
            if (!value.result().caseId().equals(caseId)) throw new SecurityException("B2 child artifact case mismatch");
            return value;
        });
    }
    private static String required(String value) {
        String result = Objects.requireNonNull(value, "caseId").trim();
        if (result.isEmpty()) throw new IllegalArgumentException("caseId cannot be blank"); return result;
    }
}
