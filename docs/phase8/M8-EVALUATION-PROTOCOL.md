# M8 evaluation protocol — pre-registration for M8-B1 through M8-B5

## 1. Purpose and evidence classes

This document pre-registers how the accepted application will be evaluated. It does not report results. Every future result must identify one of these evidence classes:

- **[CoALA]** — a claim supported by the CoALA source paper.
- **[Fan & Lan]** — a mechanism reconstruction supported by Fan and Lan's review.
- **[METHOD REFERENCE]** — CoT, ToT, RAP/MCTS or another algorithmic comparator; not an additional theoretical foundation.
- **[ENGINEERING DECISION]** — a Java design, policy, state machine, simulator or safety boundary created for this pilot.
- **[PILOT RESULT]** — an outcome actually measured in the supply-chain pilot, with scenario, simulator, budget, procedure and limitations identified.

No pilot result will be presented as proof that a historical mechanism migrated or that a residual mechanism hypothesis is universally validated.

## 2. Evaluation subjects

### 2.1 Request and failure corpus

The M8-B1 fixture baseline is a planner-only simulator corpus. It prepares 25 immutable request inputs and evaluates proposal generation under a shared inventory/supply-chain state model; it does **not** yet claim that every registered lifecycle fault is executed end to end. Stale approval, external unknown outcome, restart, concurrent observation, duplicate delivery, model outage, and intention lifecycle assertions remain gated for M8-B4. Scenario registration is not a passing test result.

Use `M8-SCENARIO-REGISTER.csv` as the source-of-truth list. The current versioned register contains 11 request-family rows (including one compound-disruption family) and 14 failure, adversarial, concurrency, governance, recovery, restart, resource, planning, delivery and model-unavailability rows. The count is taken from the register itself; it is not inferred from the earlier narrative summary. Each scenario must receive a stable ID and a versioned fixture before comparative runs begin.

A scenario fixture must record:

1. scenario ID and fixture version;
2. deterministic random seed and named budget profile;
3. initial `SupplyChainSnapshot` and its canonical fingerprint;
4. request contract, objective, hard constraints, soft preferences, deadline and authority scope;
5. evidence items with source identity, source time, observation time, validity interval, provenance and trust classification;
6. simulator/action configuration and the exact allowed action descriptors;
7. expected hard invariants and acceptable terminal outcomes;
8. procedure budget parameters;
9. expected approval and external-effect boundary (read-only, approval required, or no dispatch).

Scenarios must be independent. A long narrative trace can be a teaching example but cannot stand in for the corpus.

### 2.2 Decision-procedure comparators

Compare the following on matched scenario fixtures:

| Comparator | Implementation position at M8-B0 | Evaluation role |
|---|---|---|
| CoT-style sequential | `SequentialReasoningPlanner` (`COT_SEQUENTIAL`) | One-path, low-branching baseline |
| ToT-inspired bounded search | `ThoughtSearchPlanner` (`TOT_BOUNDED_SEARCH`) | Structured partial action-path branching, evaluation and pruning |
| Deterministic/greedy heuristic | `GreedyDeterministicComparator` in `supply-chain-agent-evaluation`; not registered in production `PlannerRouter` | Evaluation-only one-step greedy search using the same simulator, candidate evaluator and hard constraints |
| RAP-inspired MCTS | `MctsSearchPlanner` (`RAP_INSPIRED_MCTS`) | Search over explicit simulator states/actions with bounded rollouts |
| Custom hybrid | `PlannerRouter` + `DefaultPlannerSelectionPolicy` | Evaluate the initial routing thresholds and procedure selection policy |

Do not call the MCTS implementation a faithful RAP reproduction. It uses a deterministic structured simulator as its world model.

### 2.3 Fair-comparison controls

For every matched comparison:

- same initial world snapshot, request, evidence, action definitions, objective, authority policy and evaluator version;
- same simulator transition rules and hard-constraint checks;
- deterministic seeds fixed and recorded for deterministic/search runs;
- equivalent wall-clock and resource envelopes where meaningful; record planner-specific units separately rather than pretending one expansion equals one model call;
- same stopping deadline and same maximum applicable budget dimensions (time, expansions, depth/branching, rollouts, model calls and action count);
- candidate score weights and constraint policies frozen for a run batch;
- no approval or external execution during planner-only comparison; planners produce proposals only;
- report both per-scenario results and aggregate results stratified by request family, complexity and consequence severity.

The hybrid router is evaluated as a policy, not given a separate larger budget. Record why it selected a procedure and compare that choice against the strongest applicable individual baseline on the same fixture.

### 2.4 M8-B1 fixture scope and workflow coverage

The versioned M8-B1 register contains 25 rows: 11 request families and 14 failure/adversarial/recovery/concurrency scenarios. The planner-only harness runs every fixture against five comparators (125 rows per batch). This is input coverage for planning comparison, not proof that each lifecycle failure has been executed. Approval denial/expiry, unknown external outcomes, duplicate delivery, process restart, cross-execution concurrency and model-provider outages require M8-B4 lifecycle adapters and failure injection. The `MODEL_UNAVAILABLE` fixture does not invoke a model.

The sequential comparator uses a deterministic structured decision provider, and the ToT comparator uses the deterministic action ranker. These are algorithm baselines with zero LLM calls; they do not compare prompt quality or a provider's chain-of-thought output. Every planner remains proposal-only.

The batch manifest records a planner-configuration fingerprint in addition to corpus, world-model and evaluator fingerprints. A moving fixture clock is offset to the static fixture timestamp; it advances while the fixture's business timestamps remain fixed, so both hard deadline and max-runtime checks remain active. Runtime nanoseconds are measured observations and are not expected to be byte-identical across runs.

### 2.5 Repetition and reproducibility

- Deterministic simulator scenarios must reproduce the same state transitions for the same fixture and seed.
- If a model/provider introduces nondeterminism, pin provider/model version and decoding configuration, record every request/response fingerprint, and repeat the run across a declared seed set.
- Lock the scenario corpus, evaluator version, planner parameters, seed set and budget profile before the main comparative run.
- If fixture, scoring, planner or simulator changes after a run, mark the old run as a different experiment version; never silently merge the results.
- Report sample sizes and uncertainty. Do not generalize from one hand-selected trace or hide failed/abstained runs.

## 3. Primary metrics

| Metric | Operational definition / recording rule |
|---|---|
| Request classification correctness | Correct supported-family and entity/objective interpretation divided by labeled request cases; include out-of-scope and clarification cases |
| Evidence coverage | Relevant required evidence items correctly acquired and considered divided by the fixture's labeled relevant evidence items |
| Evidence freshness | Used evidence that is valid/current at the scenario's as-of time; report stale evidence separately |
| Provenance completeness | Used evidence with source, source/observation time, validity and scope metadata divided by all used evidence |
| Retrieval usefulness | Relevant recall/precision plus latency and maintenance/resource cost; distinguish retrieved, admitted, used and outcome-attributed items |
| Context admission precision | Admitted items that are relevant to the current request/action divided by all admitted items; also report decisive-evidence recall |
| Plan feasibility | Share of selected candidates that pass every hard domain constraint on authoritative simulator replay |
| Hard-constraint violations | Count each violated invariant; required test-environment acceptance is zero |
| Fulfillment impact | Delayed orders, affected units, service-level outcome and simulated recovery time |
| Cost and resource use | Procurement/transport/inventory cost where modeled, plus compute/search/model/tool resource ledger |
| Robustness | Stability under evidence perturbation, late observations, alternative seeds and simulator boundary cases |
| Replanning frequency | Number of replans per execution; distinguish necessary replans from avoidable plan churn |
| Typed-impasse resolution | Resolution disposition, valid return rate, parent-state integrity and escalation/timeout rates |
| Intention integrity | Goal/objective fingerprint drift, unauthorized goal changes, interruption recovery and terminal correctness |
| Uncertainty/governance | Calibration/uncertainty behavior where measurable, verification effort, escalation precision and safe-stop correctness |
| External-effect safety | Unauthorized dispatches, duplicate effects, stale approval use, UNKNOWN_OUTCOME replay and audit completeness; unauthorized/duplicate/stale-use counts must be zero in controlled tests |
| Computational cost | End-to-end latency, p50/p95 where sample size supports it, search expansions, rollouts, model/tool calls and total budget consumption |

For each metric, store numerator, denominator, missing/unknown count and per-scenario raw values. Do not collapse safety violations into an average quality score.

## 4. Mechanism ablations

### 4.1 Required individual ablations

| Mechanism | Primary contrast | Required observations |
|---|---|---|
| B1 | B1 value/latency/maintenance-aware selection vs simpler retrieval/admission baseline | Relevant recall, stale-evidence rejection, decisive evidence retention, retrieval latency, context size/cost and downstream outcome attribution |
| B2 | Typed bounded impasse/substate/typed return vs generic retry/error handling | Resolution success, parent-state integrity, child budget/deadline handling, stale result rejection, escalation and invalid-plan prevention |
| B3 | Bounded typed workspace/broadcast/admission vs top-k/shared-context baseline | Capacity, decisive-evidence recall, distractor robustness, broadcast consumers/receipts, admission precision and policy-update attribution |
| B4 | Persistent intention/reconsideration vs plan-only baseline | Objective drift, method-switch correctness, stale-plan rejection, interruption/restart recovery, anti-oscillation and completion |
| B6 | Uncertainty/resource-aware control vs fixed-budget execution | Uncertainty propagation, verification effort, budget use, safe stop, escalation and interruption/resume |

### 4.2 Required interaction experiments

1. **B2 + B4:** typed recovery without intention governance versus typed recovery constrained by the persistent parent objective. Measure parent integrity, goal drift, recovery success and overhead.
2. **B1 + B3 + B6:** simple retrieval; B1 selection plus B3 competition; and B1+B3 with B6 uncertainty/resource signals. Measure decisive-evidence recall, rare-evidence suppression, workspace cost and downstream plan quality.
3. **B4 + B6:** intention-governed method switching under resource/risk meta-control. Measure correct stopping, premature stopping, unauthorized method/goal changes and completion after interruption.
4. **Full relevant composition:** compare the complete B1/B2/B3/B4/B6 composition against the strongest simpler baseline and staged additions. Do not run an indiscriminate full factorial unless sample size and scenario design justify it.

B5/GraSP remains a closed negative case; do not claim a new residual B5 implementation. If a skill/procedure comparison is included, label it as an engineering baseline, not a new B5 mechanism.

### 4.3 Seven-field trace for mechanisms

For each mechanism/run record:

- **S — Operative state substrate:** exact typed state and version consumed;
- **C — Control locus:** component/principal that authorizes the transition;
- **T — Transition trigger:** event/condition that activates it;
- **P — Persistence boundary:** durable, cycle-scoped or ephemeral state;
- **F — Failure semantics:** reject, recover, suspend, escalate, abstain or stop;
- **L — Learning operator:** evidence-attributed update, if any; otherwise state not applicable/unknown;
- **R — Resource/governance policy:** budgets, permissions, inventory/capacity/time/cost limits.

A row counts as evaluated only when state, control, trigger, persistence and failure behavior are observable and the relevant test has passed. If learning is absent or not isolated, mark it explicitly rather than claiming implicit learning.

## 5. Safety and release gates

Hard acceptance invariants for the controlled test environment:

- zero negative inventory and zero over-allocation;
- zero unqualified substitution or route/product compatibility violations;
- zero invalid/stale plan commits;
- zero unauthorized dispatches and zero stale-approval dispatches;
- zero duplicate external effects for the declared idempotent test adapters;
- no blind redispatch of UNKNOWN_OUTCOME;
- no child resolution that changes its parent objective or bypasses parent-version checks;
- no false report that an unresolved order is fulfilled;
- safe stop/escalation when evidence, model output or planning budget is insufficient.

Performance/quality targets beyond these hard invariants will be stated and approved before the main M8-B2 comparison runs. They will not be chosen retroactively after results are visible.

## 6. Result artifacts

Each experiment batch will emit:

1. versioned scenario manifest and fixture fingerprints;
2. run manifest (code baseline, provider/model version if used, simulator/evaluator version, planner config, seed, budgets and timestamps);
3. machine-readable per-run result rows (JSONL or CSV) with raw metric numerators/denominators;
4. structured decision trace and mechanism S/C/T/P/F/L/R trace;
5. failure/abstention/unknown-result records;
6. aggregate comparison tables and plots with sample counts and uncertainty;
7. a narrative report separating source-paper claims, engineering decisions and measured pilot results.

No experiment result is accepted without the exact code baseline and scenario/evaluator version to reproduce it.
