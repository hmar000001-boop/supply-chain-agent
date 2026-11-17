package dev.supplychain.agent.runtime.langchain4j;

import java.util.List;

/** Simple structured-output DTO using scalar values and lists supported by LangChain4j AI Services. */
public record ReasoningModelResponse(String summary,
                                     List<String> evidenceBackedFindings,
                                     List<String> hypotheses,
                                     List<String> assumptions,
                                     List<String> supportingEvidenceKeys,
                                     List<String> informationGaps,
                                     String confidence,
                                     String conciseRationale) {
    public ReasoningModelResponse {
        evidenceBackedFindings = evidenceBackedFindings == null ? List.of() : List.copyOf(evidenceBackedFindings);
        hypotheses = hypotheses == null ? List.of() : List.copyOf(hypotheses);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        supportingEvidenceKeys = supportingEvidenceKeys == null ? List.of() : List.copyOf(supportingEvidenceKeys);
        informationGaps = informationGaps == null ? List.of() : List.copyOf(informationGaps);
    }
}
