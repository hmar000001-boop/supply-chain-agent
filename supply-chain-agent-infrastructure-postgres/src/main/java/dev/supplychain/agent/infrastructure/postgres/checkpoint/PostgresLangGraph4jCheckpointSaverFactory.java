package dev.supplychain.agent.infrastructure.postgres.checkpoint;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.bsc.langgraph4j.state.AgentState;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Production saver bootstrap sharing the managed JDBC pool used by business persistence. */
public final class PostgresLangGraph4jCheckpointSaverFactory {
    private PostgresLangGraph4jCheckpointSaverFactory() { }

    /**
     * Creates the v1 PostgreSQL saver for the pinned LangGraph4j 1.8.20 dependency.
     * The graph's serializer is mandatory and the DataSource is supplied by the application host,
     * so pool limits, TLS and credential rotation remain governed by the same connection boundary.
     */
    public static <S extends AgentState> PostgresSaver create(DataSource dataSource, StateGraph<S> graph) throws Exception {
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(graph, "graph");
        return PostgresSaver.builder()
                .datasource(dataSource)
                .stateSerializer(graph.getStateSerializer())
                .createTables(true)
                .build();
    }

    /** Stable per-scope/per-execution identity: restarts resume the same graph, other executions cannot collide. */
    public static String threadIdForExecution(String scopeId, String executionId) {
        String scope = required(scopeId, "scopeId");
        String execution = required(executionId, "executionId");
        return "sca-" + sha256(scope + "\n" + execution);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
