package dev.supplychain.agent.runtime.langchain4j;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.supplychain.agent.actions.internal.ReasoningAssessment;
import dev.supplychain.agent.actions.internal.ReasoningConfidence;
import dev.supplychain.agent.actions.internal.ReasoningProvider;
import dev.supplychain.agent.actions.internal.ReasoningRequest;
import dev.supplychain.agent.memory.common.EvidenceReference;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** LangChain4j stable-core adapter. It returns a typed assessment and cannot invoke tools. */
public final class LangChain4jReasoningProvider implements ReasoningProvider {
    private final ReasoningAiService aiService;

    public LangChain4jReasoningProvider(ChatModel chatModel) {
        this.aiService = AiServices.create(ReasoningAiService.class, Objects.requireNonNull(chatModel, "chatModel"));
    }

    @Override public ReasoningAssessment assess(ReasoningRequest request) {
        Objects.requireNonNull(request, "request");
        Map<String, EvidenceReference> suppliedEvidence = new LinkedHashMap<>();
        request.evidence().forEach(ref -> suppliedEvidence.put(ref.evidenceType() + ":" + ref.evidenceId(), ref));
        String prompt = "Task:\n" + request.task() + "\n\nContext:\n" + request.context()
                + "\n\nAllowed evidence keys (cite only these exact keys):\n"
                + (suppliedEvidence.isEmpty() ? "(none)" : String.join("\n", suppliedEvidence.keySet()));
        ReasoningModelResponse response = Objects.requireNonNull(aiService.assess(prompt), "LangChain4j returned no assessment");
        var citations = response.supportingEvidenceKeys().stream().distinct().map(key -> {
            EvidenceReference reference = suppliedEvidence.get(key);
            if (reference == null) throw new IllegalArgumentException("Model cited evidence not supplied by caller: " + key);
            return reference;
        }).toList();
        ReasoningConfidence confidence;
        try {
            confidence = ReasoningConfidence.valueOf(response.confidence() == null
                    ? "UNASSESSED" : response.confidence().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            confidence = ReasoningConfidence.UNASSESSED;
        }
        return new ReasoningAssessment(response.summary(), response.evidenceBackedFindings(),
                response.hypotheses(), response.assumptions(), citations,
                response.informationGaps(), confidence, response.conciseRationale());
    }
}
