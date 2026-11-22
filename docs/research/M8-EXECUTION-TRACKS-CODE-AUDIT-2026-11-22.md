# Execution-track decision from the current source audit

See [`M8-MECHANISM-EXPERIMENT-PIPELINE-CI-FIX3.md`](M8-MECHANISM-EXPERIMENT-PIPELINE-CI-FIX3.md), section “Execution-track architecture audit”.

Decision: do not introduce a second graph implementation. Retain the deterministic/scripted reasoning + deterministic supply-chain simulator as the primary mechanism-evaluation track. Add an optional real-LLM-in-the-loop profile later, injecting an explicitly configured LangChain4j `ChatModel` through the existing `RequestInterpreter` and `ReasoningProvider` ports while keeping the same deterministic world simulator.
