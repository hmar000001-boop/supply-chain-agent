package dev.supplychain.agent.runtime;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphResult;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs in CI when -Dphase0.postgres=true and the PostgreSQL service is available.
 * A second saver/graph instance reads the checkpoint before resuming it, simulating
 * application-process replacement without claiming an OS-level kill test.
 */
class PostgresCheckpointCompatibilityTest {

    private static final String STATUS = "status";
    private static final String APPROVAL = "approval";

    @Test
    @EnabledIfSystemProperty(named = "phase0.postgres", matches = "true")
    void postgresCheckpointCanBeReloadedByANewSaverAndResumed() throws Exception {
        String threadId = "phase0-postgres-" + UUID.randomUUID();
        var builder = new StateGraph<>(ApprovalState.SCHEMA, ApprovalState::new)
                .addNode("prepare", node_async(state -> Map.of(STATUS, "WAITING_FOR_APPROVAL")))
                .addNode("approval", node_async(state -> Map.of()))
                .addNode("finish", node_async(state -> Map.of(STATUS, "COMPLETED")))
                .addEdge(START, "prepare")
                .addEdge("prepare", "approval")
                .addEdge("approval", "finish")
                .addEdge("finish", END);

        var firstSaver = newSaver(builder);
        var firstGraph = builder.compile(CompileConfig.builder()
                .checkpointSaver(firstSaver)
                .releaseThread(false)
                .interruptBefore("approval")
                .build());
        var config = RunnableConfig.builder().threadId(threadId).build();

        var firstRun = firstGraph.stream(Map.of(STATUS, "CREATED"), config);
        for (var ignored : firstRun) {
            // Stop at the approval breakpoint; the checkpoint is committed to PostgreSQL.
        }
        assertTrue(GraphResult.from(firstRun).isInterruptionMetadata());

        assertEquals("WAITING_FOR_APPROVAL", firstGraph.getState(config).state().value(STATUS).orElse(""));

        // A new saver instance discards the first saver cache and must reload the DB checkpoint.
        var secondSaver = newSaver(builder);
        var resumedGraph = builder.compile(CompileConfig.builder()
                .checkpointSaver(secondSaver)
                .releaseThread(false)
                .interruptBefore("approval")
                .build());

        assertEquals("WAITING_FOR_APPROVAL", resumedGraph.getState(config).state().value(STATUS).orElse(""));
        var updateConfig = resumedGraph.updateState(config, Map.of(APPROVAL, "APPROVED"), null);
        for (var ignored : resumedGraph.stream(GraphInput.resume(), updateConfig)) {
            // Continue from the database checkpoint after the new saver has loaded it.
        }

        var completed = resumedGraph.getState(updateConfig).state();
        assertEquals("COMPLETED", completed.value(STATUS).orElse(""));
        assertEquals("APPROVED", completed.value(APPROVAL).orElse(""));
    }

    /**
     * Builds the JDBC-backed checkpoint saver and initializes its schema when requested.
     * Database connection and schema-initialization failures are deliberately propagated
     * to the integration test rather than hidden or converted into a skipped test.
     */
    private static PostgresSaver newSaver(StateGraph<ApprovalState> builder) throws SQLException {
        return PostgresSaver.builder()
                .host(env("PGHOST", "localhost"))
                .port(Integer.parseInt(env("PGPORT", "5432")))
                .user(env("PGUSER", "postgres"))
                .password(env("PGPASSWORD", "postgres"))
                .database(env("PGDATABASE", "supply_chain_agent"))
                .stateSerializer(builder.getStateSerializer())
                .createTables(true)
                .build();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static final class ApprovalState extends AgentState {
        private static final Map<String, Channel<?>> SCHEMA = Map.of(
                STATUS, Channels.base(() -> "CREATED"),
                APPROVAL, Channels.base(() -> "PENDING")
        );

        private ApprovalState(Map<String, Object> initialData) {
            super(initialData);
        }
    }
}
