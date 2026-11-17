package dev.supplychain.agent.app;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.supplychain.agent.runtime.langchain4j.LangChain4jRequestInterpreter;
import dev.supplychain.agent.runtime.langgraph.SupplyChainGraphState;
import dev.supplychain.agent.runtime.langgraph.SupplyChainIntakeGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;

import java.util.Map;
import java.util.UUID;

/** Local deterministic smoke application. No provider credentials are required. */
public final class Phase0Application {

    private Phase0Application() {
    }

    public static void main(String[] args) throws Exception {
        var interpreter = new LangChain4jRequestInterpreter(new FixedResponseChatModel("READY"));
        var graph = SupplyChainIntakeGraph.compile(interpreter, new MemorySaver());
        var config = RunnableConfig.builder()
                .threadId("phase0-local-smoke-" + UUID.randomUUID())
                .build();
        var finalOutput = graph.invokeFinal(
                        GraphInput.args(Map.<String, Object>of(
                                SupplyChainGraphState.REQUEST,
                                "Assess the impact of a supplier capacity reduction on order SO-100.")),
                        config)
                .orElseThrow(() -> new IllegalStateException(
                        "The intake graph produced no final output"));

        if (!finalOutput.isEND()) {
            throw new IllegalStateException(
                    "The local smoke graph was interrupted before completion at node: "
                            + finalOutput.node());
        }

        SupplyChainGraphState result = finalOutput.state();
        System.out.println("Outcome: " + result.outcome());
        System.out.println("Intent: " + result.normalizedIntent());
        System.out.println("Summary: " + result.summary());
    }

    private static final class FixedResponseChatModel implements ChatModel {
        private final String response;

        private FixedResponseChatModel(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(AiMessage.from(response)).build();
        }
    }
}
