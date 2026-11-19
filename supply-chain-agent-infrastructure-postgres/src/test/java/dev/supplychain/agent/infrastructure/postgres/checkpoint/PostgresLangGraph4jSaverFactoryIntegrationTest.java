package dev.supplychain.agent.infrastructure.postgres.checkpoint;

import dev.supplychain.agent.infrastructure.postgres.config.PostgresDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;
import dev.supplychain.agent.infrastructure.postgres.migration.PostgresMigrationRunner;
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

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresLangGraph4jSaverFactoryIntegrationTest {
    private static final String STATUS = "status";
    private static final String APPROVAL = "approval";

    @Test
    void executionThreadIdentityIsStableAndScopeSeparated() {
        String execution = "execution-" + UUID.randomUUID();
        String first = PostgresLangGraph4jCheckpointSaverFactory.threadIdForExecution("tenant-a", execution);
        assertEquals(first, PostgresLangGraph4jCheckpointSaverFactory.threadIdForExecution("tenant-a", execution));
        assertNotEquals(first, PostgresLangGraph4jCheckpointSaverFactory.threadIdForExecution("tenant-b", execution));
        assertNotEquals(first, PostgresLangGraph4jCheckpointSaverFactory.threadIdForExecution("tenant-a", execution + "-other"));
    }

    @Test
    @EnabledIfSystemProperty(named = "phase0.postgres", matches = "true")
    void productionSaverFactoryUsesSharedDataSourceForPauseAndRestartResume() throws Exception {
        DataSource dataSource = PostgresDataSourceFactory.create(PostgresDatabaseConfig.fromEnvironment(System.getenv()));
        assertEquals(PostgresMigrationRunner.supportedMigrationCount(),
                new PostgresMigrationRunner(dataSource).migrate().totalMigrations());
        String threadId = PostgresLangGraph4jCheckpointSaverFactory.threadIdForExecution(
                "m7b2-checkpoint-scope", UUID.randomUUID().toString());
        var builder = new StateGraph<>(ApprovalState.SCHEMA, ApprovalState::new)
                .addNode("prepare", node_async(state -> Map.of(STATUS, "WAITING_FOR_APPROVAL")))
                .addNode("approval", node_async(state -> Map.of()))
                .addNode("finish", node_async(state -> Map.of(STATUS, "COMPLETED")))
                .addEdge(START, "prepare")
                .addEdge("prepare", "approval")
                .addEdge("approval", "finish")
                .addEdge("finish", END);

        PostgresSaver firstSaver = PostgresLangGraph4jCheckpointSaverFactory.create(dataSource, builder);
        var firstGraph = builder.compile(CompileConfig.builder()
                .checkpointSaver(firstSaver).releaseThread(false).interruptBefore("approval").build());
        var config = RunnableConfig.builder().threadId(threadId).build();
        var firstRun = firstGraph.stream(GraphInput.args(Map.<String, Object>of(STATUS, "CREATED")), config);
        for (var ignored : firstRun) {
            // Drain until the configured approval interrupt commits its checkpoint.
        }
        assertTrue(GraphResult.from(firstRun).isInterruptionMetadata());
        assertEquals("WAITING_FOR_APPROVAL", firstGraph.getState(config).state().value(STATUS).orElse(""));

        // A distinct saver built from the same DataSource must reload the committed checkpoint.
        PostgresSaver replacementSaver = PostgresLangGraph4jCheckpointSaverFactory.create(dataSource, builder);
        var replacementGraph = builder.compile(CompileConfig.builder()
                .checkpointSaver(replacementSaver).releaseThread(false).interruptBefore("approval").build());
        assertEquals("WAITING_FOR_APPROVAL", replacementGraph.getState(config).state().value(STATUS).orElse(""));
        var updateConfig = replacementGraph.updateState(config, Map.of(APPROVAL, "APPROVED"), null);
        var terminal = replacementGraph.invokeFinal(GraphInput.resume(), updateConfig)
                .orElseThrow(() -> new AssertionError("PostgreSQL-backed graph produced no terminal result"));
        assertEquals(END, terminal.node());
        assertEquals("COMPLETED", terminal.state().value(STATUS).orElse(""));
        assertEquals("APPROVED", terminal.state().value(APPROVAL).orElse(""));
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
