package dev.supplychain.agent.runtime;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.supplychain.agent.core.interpretation.InterpretationOutcome;
import dev.supplychain.agent.core.interpretation.InterpretationRequest;
import dev.supplychain.agent.runtime.langchain4j.LangChain4jRequestInterpreter;
import dev.supplychain.agent.runtime.langgraph.SupplyChainGraphState;
import dev.supplychain.agent.runtime.langgraph.SupplyChainIntakeGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphResult;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameworkCompatibilityTest {

    @Test
    void langchain4jAiServiceAdapterMapsOnlyKnownOutcomes() {
        var model = new ScriptedChatModel("READY");
        var interpreter = new LangChain4jRequestInterpreter(model);

        var result = interpreter.interpret(new InterpretationRequest(
                "Assess a supplier capacity reduction affecting orders SO-100 and SO-101.", "", 0));

        assertEquals(InterpretationOutcome.READY, result.outcome());
        assertEquals("READY", result.normalizedIntent());
        assertEquals(1, model.requests().size());
    }

    @Test
    void unknownModelClassificationFailsClosed() {
        var interpreter = new LangChain4jRequestInterpreter(new ScriptedChatModel("APPROVE_PURCHASE_ORDER"));

        var result = interpreter.interpret(new InterpretationRequest("Increase supplier order", "", 0));

        assertEquals(InterpretationOutcome.UNKNOWN, result.outcome());
        assertTrue(result.summary().contains("no action is authorized"));
    }

    @Test
    void langGraph4jRoutesToRealClarificationInterruptAndResumesThroughLangChain4jService() throws Exception {
        var model = new ScriptedChatModel("NEEDS_CLARIFICATION", "READY");
        var interpreter = new LangChain4jRequestInterpreter(model);
        var graph = SupplyChainIntakeGraph.compile(interpreter, new MemorySaver());
        var config = RunnableConfig.builder().threadId("phase0-cycle-" + UUID.randomUUID()).build();

        var firstRun = graph.stream(Map.of(
                SupplyChainGraphState.REQUEST,
                "Investigate a supplier disruption affecting several orders."), config);
        for (var ignored : firstRun) {
            // The graph pauses before await-clarification, after the model requested user input.
        }
        assertTrue(GraphResult.from(firstRun).isInterruptionMetadata());

        var paused = graph.getState(config).state();
        assertEquals(InterpretationOutcome.NEEDS_CLARIFICATION.name(), paused.outcome());
        assertEquals(0, paused.clarificationRound());
        assertFalse(paused.events().contains("user-clarification-recorded:1"));

        var updateConfig = graph.updateState(config, Map.of(
                SupplyChainGraphState.CLARIFICATION_CONTEXT,
                "The affected customer orders are SO-100 and SO-101.",
                SupplyChainGraphState.CLARIFICATION_ROUND,
                1
        ), null);

        for (var ignored : graph.stream(GraphInput.resume(), updateConfig)) {
            // The graph records the actual human clarification, re-runs the interpreter and completes.
        }

        var completed = graph.getState(updateConfig).state();
        assertEquals(InterpretationOutcome.READY.name(), completed.outcome());
        assertEquals(1, completed.clarificationRound());
        assertEquals(2, model.requests().size());
        assertTrue(completed.events().contains("user-clarification-recorded:1"));
        assertTrue(completed.events().contains("intake-finished"));
        assertFalse(completed.summary().isBlank());
    }

    @Test
    void clarificationCycleIsBoundedToOneRound() throws Exception {
        var model = new ScriptedChatModel("NEEDS_CLARIFICATION", "NEEDS_CLARIFICATION");
        var graph = SupplyChainIntakeGraph.compile(
                new LangChain4jRequestInterpreter(model), new MemorySaver());
        var config = RunnableConfig.builder().threadId("phase0-bounded-" + UUID.randomUUID()).build();

        var firstRun = graph.stream(Map.of(
                SupplyChainGraphState.REQUEST,
                "Investigate an unspecified supply-chain disruption."), config);
        for (var ignored : firstRun) {
            // First clarification interrupt.
        }
        assertTrue(GraphResult.from(firstRun).isInterruptionMetadata());
        var updateConfig = graph.updateState(config, Map.of(
                SupplyChainGraphState.CLARIFICATION_CONTEXT, "The affected order is SO-404.",
                SupplyChainGraphState.CLARIFICATION_ROUND, 1
        ), null);
        for (var ignored : graph.stream(GraphInput.resume(), updateConfig)) {
            // The second clarification request must terminate rather than loop forever.
        }

        var completed = graph.getState(updateConfig).state();
        assertEquals(InterpretationOutcome.NEEDS_CLARIFICATION.name(), completed.outcome());
        assertEquals(1, completed.clarificationRound());
        assertEquals(2, model.requests().size());
        assertTrue(completed.events().contains("intake-finished"));
    }

    @Test
    void localIntakeGraphCanCompleteWithoutClarification() throws Exception {
        var interpreter = new LangChain4jRequestInterpreter(new ScriptedChatModel("READY"));
        var graph = SupplyChainIntakeGraph.compile(interpreter, new MemorySaver());
        var config = RunnableConfig.builder().threadId("phase0-ready-" + UUID.randomUUID()).build();

        var result = graph.execute(Map.of(
                SupplyChainGraphState.REQUEST,
                "Assess the impact of a supplier capacity reduction on order SO-100."), config);

        assertEquals(InterpretationOutcome.READY.name(), result.get(SupplyChainGraphState.OUTCOME));
        assertEquals("READY", result.get(SupplyChainGraphState.NORMALIZED_INTENT));
    }

    private static final class ScriptedChatModel implements ChatModel {
        private final Queue<String> responses = new ConcurrentLinkedQueue<>();
        private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

        private ScriptedChatModel(String... scriptedResponses) {
            responses.addAll(List.of(scriptedResponses));
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            String response = responses.poll();
            if (response == null) {
                throw new IllegalStateException("No scripted model response remains");
            }
            return ChatResponse.builder().aiMessage(AiMessage.from(response)).build();
        }

        private List<ChatRequest> requests() {
            return List.copyOf(requests);
        }
    }
}
