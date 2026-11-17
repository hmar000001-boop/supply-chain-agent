package dev.supplychain.agent.runtime.langgraph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small, serializable orchestration state for the Phase 0 compatibility proof. */
public final class SupplyChainGraphState extends AgentState {

    public static final String REQUEST = "request";
    public static final String CLARIFICATION_CONTEXT = "clarificationContext";
    public static final String CLARIFICATION_ROUND = "clarificationRound";
    public static final String OUTCOME = "outcome";
    public static final String NORMALIZED_INTENT = "normalizedIntent";
    public static final String SUMMARY = "summary";
    public static final String EVENTS = "events";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            REQUEST, Channels.base(() -> ""),
            CLARIFICATION_CONTEXT, Channels.base(() -> ""),
            CLARIFICATION_ROUND, Channels.base(() -> 0),
            OUTCOME, Channels.base(() -> "UNKNOWN"),
            NORMALIZED_INTENT, Channels.base(() -> ""),
            SUMMARY, Channels.base(() -> ""),
            EVENTS, Channels.appender(ArrayList::new)
    );

    public SupplyChainGraphState(Map<String, Object> initialData) {
        super(initialData);
    }

    public String request() {
        return this.<String>value(REQUEST).orElse("");
    }

    public String clarificationContext() {
        return this.<String>value(CLARIFICATION_CONTEXT).orElse("");
    }

    public int clarificationRound() {
        return this.<Integer>value(CLARIFICATION_ROUND).orElse(0);
    }

    public String outcome() {
        return this.<String>value(OUTCOME).orElse("UNKNOWN");
    }

    public String normalizedIntent() {
        return this.<String>value(NORMALIZED_INTENT).orElse("");
    }

    public String summary() {
        return this.<String>value(SUMMARY).orElse("");
    }

    public List<String> events() {
        return this.<List<String>>value(EVENTS).orElse(List.of());
    }
}
