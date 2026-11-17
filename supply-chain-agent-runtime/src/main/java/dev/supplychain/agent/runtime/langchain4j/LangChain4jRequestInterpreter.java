package dev.supplychain.agent.runtime.langchain4j;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.supplychain.agent.core.interpretation.InterpretationOutcome;
import dev.supplychain.agent.core.interpretation.InterpretationRequest;
import dev.supplychain.agent.core.interpretation.RequestInterpretation;
import dev.supplychain.agent.core.interpretation.RequestInterpreter;

import java.util.Locale;
import java.util.Objects;

/**
 * LangChain4j adapter for the framework-neutral RequestInterpreter port.
 * Model output is normalized into a closed enum; unknown output fails closed.
 */
public final class LangChain4jRequestInterpreter implements RequestInterpreter {

    private final RequestClassificationAiService aiService;

    public LangChain4jRequestInterpreter(ChatModel chatModel) {
        Objects.requireNonNull(chatModel, "chatModel");
        this.aiService = AiServices.create(RequestClassificationAiService.class, chatModel);
    }

    @Override
    public RequestInterpretation interpret(InterpretationRequest request) {
        Objects.requireNonNull(request, "request");
        String input = "Request: " + request.originalText()
                + "\nClarification context: " + request.clarificationContext()
                + "\nClarification round: " + request.clarificationRound();
        String raw = aiService.classify(input);
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);

        InterpretationOutcome outcome = switch (normalized) {
            case "READY" -> InterpretationOutcome.READY;
            case "NEEDS_CLARIFICATION" -> InterpretationOutcome.NEEDS_CLARIFICATION;
            case "OUT_OF_SCOPE" -> InterpretationOutcome.OUT_OF_SCOPE;
            default -> InterpretationOutcome.UNKNOWN;
        };

        String summary = switch (outcome) {
            case READY -> "The request is sufficiently specified for deterministic domain validation.";
            case NEEDS_CLARIFICATION -> "Required information is missing or ambiguous.";
            case OUT_OF_SCOPE -> "The request is outside the supported supply-chain capability boundary.";
            case UNKNOWN -> "The model returned an unrecognized classification; no action is authorized.";
        };

        return new RequestInterpretation(outcome, normalized, summary);
    }
}
