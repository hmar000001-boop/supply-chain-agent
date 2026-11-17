package dev.supplychain.agent.runtime;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckpointResumeCompatibilityTest {

    private static final String STATUS = "status";
    private static final String APPROVAL = "approval";

    @Test
    void graphCanPauseUpdateStateAndResumeUsingMemorySaver() throws Exception {
        var builder = new StateGraph<>(ApprovalState.SCHEMA, ApprovalState::new)
                .addNode("prepare", node_async(state -> Map.of(STATUS, "WAITING_FOR_APPROVAL")))
                .addNode("approval", node_async(state -> Map.of()))
                .addNode("finish", node_async(state -> Map.of(STATUS, "COMPLETED")))
                .addEdge(START, "prepare")
                .addEdge("prepare", "approval")
                .addEdge("approval", "finish")
                .addEdge("finish", END);

        var saver = new MemorySaver();
        var graph = builder.compile(CompileConfig.builder()
                .checkpointSaver(saver)
                .interruptBefore("approval")
                .build());
        var config = RunnableConfig.builder().threadId("phase0-approval-" + UUID.randomUUID()).build();

        var firstRun = graph.stream(GraphInput.args(Map.<String, Object>of(STATUS, "CREATED")), config);
        for (var ignored : firstRun) {
            // Consume updates until LangGraph4j interrupts before the approval node.
        }

        var paused = graph.getState(config).state();
        assertEquals("WAITING_FOR_APPROVAL", paused.value(STATUS).orElse(""));

        var updatedConfig = graph.updateState(config, Map.of(APPROVAL, "APPROVED"), null);
        // updateState returns a config pinned to the checkpoint that contains the approval update.
        // Use invokeFinal's terminal output for the resumed execution; getState(updatedConfig) would
        // intentionally read that historical checkpoint, not the later checkpoints from the resume.
        var resumedOutput = graph.invokeFinal(GraphInput.resume(), updatedConfig)
                .orElseThrow(() -> new AssertionError("Resumed graph produced no terminal output"));
        assertEquals(END, resumedOutput.node(), "Resumed graph must reach END rather than pause again");

        var completed = resumedOutput.state();
        assertEquals("COMPLETED", completed.value(STATUS).orElse(""));
        assertEquals("APPROVED", completed.value(APPROVAL).orElse(""));
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
