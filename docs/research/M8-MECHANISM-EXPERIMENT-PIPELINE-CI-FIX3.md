# M8 mechanism experiment pipeline — CI Fix 3

Date: 2026-11-22
Candidate: `M8-GAP01-GAP05-MECHANISM-EXPERIMENT-HARNESS-V1-CI-FIX3`

## Hosted CI failure

The 2026-11-22 16:00 UTC hosted Maven run compiled all twelve reactor modules and ran the evaluation module. The single failing test was `M8B4FullGraphWorkflowTest.fullGraphExperimentAdapterRunsMatchedIsolatedTrainingArmsWithoutDispatch`.

`FullGraphExperimentAdapter.validateIsolationAndMatching` rejected the workspace consumer registered for the baseline and treatment sessions because both references were identical.

The test-only `ScenarioRuntime` constructor created that consumer with a stateless lambda at one call site. The JVM is permitted to cache/reuse a stateless lambda instance. Constructing two `ScenarioRuntime` objects therefore did not guarantee two distinct consumer adapter objects. This is an object-identity/isolation fixture defect, not evidence that the graph runtimes or the B3 workspace repositories were shared.

## Correction

The test fixture now constructs an explicit anonymous `WorkspaceConsumer` object inside each `ScenarioRuntime` constructor invocation. Each invocation therefore creates a new consumer adapter. The adapter's fail-closed identity check remains unchanged.

The full-graph regression test also asserts that the two sessions have different `workspaceConsumers().get("planner")` references. It still verifies distinct runtime instances, approval-boundary stopping, identical graph inputs, matching request fingerprints, and zero external side effects.

No production graph, mechanism, authorization, simulator, or evidence-verifier code changes in this fix.

## Execution-track architecture audit

The current code already separates language/model ports from the supply-chain world simulator:

- `core/.../interpretation/RequestInterpreter` is a framework-neutral port. `runtime/.../LangChain4jRequestInterpreter` adapts a supplied LangChain4j `ChatModel` to it.
- `actions/.../internal/ReasoningProvider` is a framework-neutral reasoning port. `runtime/.../LangChain4jReasoningProvider` adapts a supplied LangChain4j `ChatModel` to typed, evidence-bounded `ReasoningAssessment` values. It validates citations against evidence supplied by the caller and exposes no tools.
- `FullGraphEvaluationHost.Ports` receives a `RequestInterpreter`; evaluation code can inject deterministic or model-backed behavior without rebuilding the graph.
- `SupplyChainSimulator` and `SimulatorBackedPlanningWorldModel` are deterministic world-transition components. They do not simulate language generation or LLM reasoning.
- `IndependentSimulatorPlanVerifier` creates fresh replay world/evaluator instances, replays the exact graph-selected proposal, and verifies the resulting plan evaluation without dispatching external actions.

The current executable paths are deterministic rather than live-LLM paths:

- `Phase0Application` supplies a private `FixedResponseChatModel("READY")` to the LangChain4j interpreter.
- `FrameworkCompatibilityTest` supplies a `ScriptedChatModel` with predetermined responses.
- The M8 full-graph workflow test supplies a deterministic `RequestInterpreter` lambda.
- Where the test graph registers `ReasoningAction`, the tests use a deterministic `ReasoningProvider` lambda under the budget wrapper.

The Maven dependencies include LangChain4j's core API (`dev.langchain4j:langchain4j`), but no provider-specific runtime module/configuration. Thus a real model can be injected through the existing `ChatModel` seams by a host that supplies an implementation, but the repository does not currently ship a configured live provider or a model-in-the-loop experiment profile.

## Decision: dual-track execution

A large dual-track implementation is not required for the present deterministic M8 B1–B6 mechanism experiments. Their immediate goal is controlled mechanism comparison with reproducible inputs and simulator-derived consequences. Introducing stochastic LLM outputs into those primary pairs now would add a second causal variable and make failures harder to attribute.

A separate model-in-the-loop track is nevertheless required before making claims about how the mechanisms behave with real LLM reasoning. It should reuse the same graph, mechanisms, simulator, scenario fingerprints and evidence boundaries; only the reasoning-provider profile should differ. The deterministic track remains the primary reproducible baseline, and the model-in-the-loop track uses a real, explicitly configured `ChatModel` while retaining the deterministic world simulator.

The missing work for that later track is bounded and explicit:

1. A provider-specific `ChatModel` implementation/configuration supplied through an application-owned factory; provider secrets must not enter fixtures or artifacts.
2. A versioned execution profile binding both `RequestInterpreter` and, where the graph uses it, `ReasoningProvider`.
3. Per-invocation provenance: provider/model/version, prompt/template fingerprint, decoding parameters, timeout/retry policy, request fingerprint, response hash and token/cost metadata where available.
4. A replayable trace mode for paired comparisons where the inputs are identical; when treatment changes model inputs, use replicated, randomized model-in-loop pairs and report stochastic variation rather than replaying an inapplicable response.
5. Explicit fault fixtures for malformed outputs, unrecognized classifications, unsupported evidence citations, provider timeouts and budget exhaustion.

This should be implemented as an optional evaluation profile, not as a second graph implementation and not as a replacement for deterministic simulator ground truth. The real-LLM track must continue to use the same deterministic supply-chain simulator; no live external supply-chain writes are introduced.

## Validation boundary

Static source-invariant checks and project architecture/evidence/scenario guards pass. A focused JVM smoke test confirms that the stateless-lambda call site may reuse one object while explicit anonymous-class constructions are distinct. The full Maven/Surefire/PostgreSQL reactor has not been run in this environment. Hosted CI must confirm this repair.

No TRAIN, HOLDOUT or TRANSFER confirmatory experiment was executed by this fix, and no model-quality or real-world business-effect claim is made.
