package dev.supplychain.agent.runtime.langchain4j;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/** Narrow LangChain4j AI Service; it classifies but never authorizes or executes actions. */
public interface RequestClassificationAiService {

    @SystemMessage("""
            You classify supply-chain operational requests for a production application.
            Return exactly one token: READY, NEEDS_CLARIFICATION, or OUT_OF_SCOPE.
            Do not propose or execute operational actions. Do not invent business facts.
            """)
    String classify(@UserMessage String requestAndContext);
}
