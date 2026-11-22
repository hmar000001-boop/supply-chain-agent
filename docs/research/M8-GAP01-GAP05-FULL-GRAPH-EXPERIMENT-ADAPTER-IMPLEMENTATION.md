# M8 GAP-01–GAP-05 Full-Graph Experiment Adapter

**Adapter:** `M8FullGraphExperimentAdapter-v1`  
**Protocol:** `M8-GAP01-05-IEP-1.1`  
**Status:** TRAIN telemetry and verified HOLDOUT admission paths implemented; host composition and hosted CI required  
**Evidence status:** No 280-unit TRAIN telemetry batch, typed TRAIN artifact bundle, HOLDOUT pair or TRANSFER run has been executed. See `M8-GAP01-GAP05-MECHANISM-EXPERIMENT-AND-INDEPENDENT-EVIDENCE-PIPELINE.md` for the mechanism-specific pipeline.

## 1. What this increment does

The evaluation module now has a reusable matched-pair executor that invokes the actual compiled `CognitiveCycleApplicationGraph` through `FullGraphEvaluationHost`. It does not substitute planner-only execution for the full graph.

For one generated TRAIN unit and one mechanism, the adapter:

1. Verifies that the unit matches the exact preregistered TRAIN unit registry, including fixture and effective-input fingerprints.
2. Creates separate baseline and treatment sessions through `FullGraphExperimentSessionFactory`.
3. Requires distinct graph runtimes, compiled graphs, ports, decision/lifecycle/authority adapters, action registries, world-model/evaluator instances, planner strategy instances, workspace consumers, and B1/B2/B3 state repositories.
4. Requires the same generated fixture, effective-input fingerprint, exact graph input map, request fingerprint, world/evaluator configuration fingerprints, action descriptors and planner identities across the two arms. It also requires distinct sandbox attestations and audit counters, B1 selectors/learning services/repositories, B3 services, action registries, planner objects and other mutable graph adapters.
5. Orders baseline/treatment execution deterministically from the unit ID and mechanism, avoiding ambient randomness.
6. Runs each real graph only to its first terminal or interruption boundary. It never resumes clarification or approval and never supplies approval response fields in graph input.
7. Checks sandbox counters before and after each graph run. Any external network request, external action dispatch, state-changing dispatch, or dispatch after approval invalidates the pair.
8. Captures state signals, event traces, event fingerprints and deterministically ordered metric maps in raw JSONL-compatible records. Observation fingerprints exclude ephemeral session IDs but bind the unit, fixture/effective-input fingerprints, mechanism, arm configuration fingerprint, graph boundary, events and metrics, so a separately provisioned replay can be compared without conflating runtime identity with measured behavior.

`FullGraphEvaluationHost.compileFullRuntime` now exposes the exact B1 feature repository/learning service, B1 selector, B2/B3 services and R2 configuration used by the compiled graph. An evaluation-only overload can inject a B6 calibration service; if used, the adapter rejects a run when an active calibration profile is found for the run's exact scope and target. B6 calibration comparisons are still shadow/offline calculations supplied by a metric collector, not production activation.

## 2. Safety and isolation contract

A provider must create a new full graph and new mutable ports for each arm. It must attest that the run is in-memory only, external connectors are disabled, production credentials are absent, and state-changing dispatch remains approval-gated. The provider must also instrument a runtime `ExperimentSandboxAudit` around its disposable action handlers/connectors.

The attestation is an explicit trusted-harness contract, not a substitute for instrumentation. The post-run counters must all have zero delta. The adapter stops at the first interruption; it does not approve plans, resume the graph, or execute pending state-changing actions. When a B6 calibration service is injected, the adapter rejects a pair if the exact scope/target has an active profile or if the graph's exact current assessment is persisted as calibrated. B6 remains shadow-only.

`conditionFingerprint` is the trusted factory's canonical fingerprint of the complete arm configuration. The adapter verifies that the two fingerprints differ while checking shared world/evaluator/action/request invariants. Since arbitrary host ports can encapsulate behavior the adapter cannot introspect, the session factory must attest that only the preregistered mechanism intervention changes between arms; it must include all treatment/control wiring in the condition fingerprint. This is a provider obligation, not an independently proven fact.

The matched-arm identity checks are intentionally strict. A harness that reuses stateful adapters across arms is rejected instead of silently producing a contaminated pair.

## 3. TRAIN telemetry gate

Adapter v1 accepts only `TRAIN` units. It rejects HOLDOUT and TRANSFER before calling the session factory. The adapter's authorized unit registry is generated from the exact preregistered split resource; a same-ID fixture with a different fixture/effective-input fingerprint is rejected.

`FullGraphExperimentTrainingBatchRunner` accepts exactly the complete 280-unit TRAIN set for one mechanism, runs every pair before writing, and writes a single output artifact atomically without overwriting an existing file. Its envelope explicitly marks the data as `FULL_GRAPH_TELEMETRY_NO_VERIFIED_OUTCOME_LABELS` and `confirmatoryInferenceAllowed=false`.

No training artifact is produced by this batch writer. It stores paired graph observations only.

## 4. Metrics and evidence boundaries

`FullGraphExperimentTelemetry` supplies descriptive instrumentation signals for graph progress, B1 access/verified-use state, B2 draft presence, B3 verified outcome counts, B4 intention/method state, and B6 raw uncertainty references/values. These fields are useful for checking that the graph traversed the expected services, but they are **not** the preregistered primary endpoints.

The primary endpoints require mechanism-specific independent verifiers and outcome collection, now wired through the separate mechanism pipeline described in `M8-GAP01-GAP05-MECHANISM-EXPERIMENT-AND-INDEPENDENT-EVIDENCE-PIPELINE.md`:

- B1 requires independently verified downstream usefulness tied to exact memory attribution.
- B2 requires approved procedure activation, a later-cycle retrieval/transfer episode, fresh descriptor checks and verifier-confirmed feasible recovery.
- B3 requires consumer receipts followed by independently verified coalition downstream outcomes.
- B4 objective satisfaction requires a separately instrumented episode outcome while preserving the committed objective and approval boundary. Adapter v1 currently stops before approval, so its graph telemetry alone cannot establish end-of-episode objective success.
- B6 requires independent simulator-grounded error labels for EVIDENCE, HYPOTHESIS and PLAN, plus an offline shadow calibration calculation and held-out score computation.

A custom `ExperimentMetricCollector` may add study-specific raw measurements, but it must not infer a verified outcome from planner score, the agent's own confidence, a proposal, or a missing external event. The adapter makes no causal estimate and does not run statistical inference. Batch output contains raw telemetry with `confirmatoryInferenceAllowed=false`; it is not a verified outcome dataset or a trained artifact.

## 5. Current integration point

A trusted harness implements `FullGraphExperimentSessionFactory`. For each `(unit, mechanism, arm)`, it:

- creates fresh `FullGraphEvaluationHost.Ports` and a fresh `FullGraphEvaluationHost`;
- builds the treatment/control wiring for exactly the named mechanism, leaving all other components identical;
- compiles through `FullGraphExperimentSession.compile(...)` with a fresh in-memory checkpoint saver;
- provides a distinct checkpoint thread (equal to the configured LangGraph4j thread ID), sandbox namespace, condition fingerprint and safety audit;
- supplies the same generated unit request and a metric collector.

The existing `M8B4FullGraphWorkflowTest` contains deterministic test-only domain/authority/action adapters and now uses them to smoke-test a real matched B4 pair. The adapter API is intended to be reused by a production-quality evaluation harness; the test adapter is not an operational integration.

## 6. Validation commands

```bash
python scripts/verify-m8-b5-architecture.py
python scripts/verify-m8-r2-evidence.py
python scripts/verify-m8-preb7-f03-f04.py
python scripts/verify-m8-fullgraph-experiment-adapter.py
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

Hosted CI must pass. The new full-graph pair smoke test is not proof that all five mechanisms have valid treatment implementations or outcome verifiers.

## 7. Remaining implementation and evidence gates

The following items are now implemented in the separate mechanism-specific pipeline document, but still require trusted host composition and/or execution: typed TRAIN artifact construction, independent B1/B2/B3/B6 artifact verification, B1/B2/B3/B4/B6 matched HOLDOUT execution, independent simulator replay, B6 label verification and exact-280 HOLDOUT JSONL writing. These capabilities are not evidence that a run has occurred.

Still open:

1. Trusted host wiring for the exact planner, consumer, authority, repositories, fresh simulator, target-specific B6 label producer and independent verifiers.
2. Actual 280-unit TRAIN collection and a frozen, independently verified TRAIN artifact bundle.
3. Actual matched HOLDOUT runs and integrity acceptance for each mechanism.
4. Profile-stratified clustered bootstrap, paired tests, Holm correction, confidence intervals and minimum-practical-effect decisions.
5. Separate frozen-artifact TRANSFER execution and acceptance.
6. Hosted CI acceptance for the complete research pipeline increment.

No experiment result is claimed. B1/B3 remain SHADOW/DISABLED, B6 remains NOT_CALIBRATED, TRANSFER remains closed, and simulator replay must not be described as a real-world business outcome. See `M8-GAP01-GAP05-MECHANISM-EXPERIMENT-AND-INDEPENDENT-EVIDENCE-PIPELINE.md` for APIs, responsibilities and exact gate order.
