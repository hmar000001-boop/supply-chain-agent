package dev.supplychain.agent.infrastructure.postgres.checkpoint;

import dev.supplychain.agent.infrastructure.postgres.config.PostgresPooledDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresPoolSettings;
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
        var database = PostgresDatabaseConfig.fromEnvironment(System.getenv());
        var poolSettings = new PostgresPoolSettings(4, 0, 5_000, 3_000, 30_000, 600_000, 0);
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
        var config = RunnableConfig.builder().threadId(threadId).build();

        // The first host owns a pool which is closed after committing the interrupt checkpoint.
        try (var firstDataSource = PostgresPooledDataSourceFactory.create(database, poolSettings)) {
            assertEquals(PostgresMigrationRunner.supportedMigrationCount(),
                    new PostgresMigrationRunner(firstDataSource).migrate().totalMigrations());
            PostgresSaver firstSaver = PostgresLangGraph4jCheckpointSaverFactory.create(firstDataSource, builder);
            var firstGraph = builder.compile(CompileConfig.builder()
                    .checkpointSaver(firstSaver).releaseThread(false).interruptBefore("approval").build());
            var firstRun = firstGraph.stream(GraphInput.args(Map.<String, Object>of(STATUS, "CREATED")), config);
            for (var ignored : firstRun) {
                // Drain until the configured approval interrupt commits its checkpoint.
            }
            assertTrue(GraphResult.from(firstRun).isInterruptionMetadata());
            assertEquals("WAITING_FOR_APPROVAL", firstGraph.getState(config).state().value(STATUS).orElse(""));
        }

        // A replacement host with a distinct pool must reload and resume the durable checkpoint.
        try (var replacementDataSource = PostgresPooledDataSourceFactory.create(database, poolSettings)) {
            PostgresSaver replacementSaver = PostgresLangGraph4jCheckpointSaverFactory.create(replacementDataSource, builder);
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
