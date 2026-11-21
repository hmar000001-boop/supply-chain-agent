# M8-PreB7 R2 — Outcome Feedback, Guarded Adaptation and Final Evidence Validation

**Status:** Implemented; awaiting one complete hosted Java 21/PostgreSQL CI acceptance cycle and scan-artifact review.  
**Baseline:** User-confirmed green R1-DEF source state, 2026-11-20.  
**Delivery shape:** One cumulative change-only ZIP; B2, B3, feedback, evaluation, security and conformance work are internal boundaries, not separate acceptance increments.

## 1. Executive result

R2 adds the missing typed and durable edges required to make future outcome-driven adaptation possible without activating an unproven policy:

- B2 resolves procedure drafts only from resolved child cases, current parent state, verified evidence and registered action descriptors. The draft service contains no execution or dispatch method. An approval moves only the review status to `APPROVED`; it is not action authorization.
- B3 persists accepted consumer receipts and separately verified downstream outcomes. A receipt is not itself a positive outcome. Persisted outcome rows are re-linked to the original accepted receipt and their proof is re-verified before the B3 service mints a package-private verified-evidence capability for training.
- B1/B3 feedback is opt-in, scope-bound and neutral by default. Verified-evidence training produces a `SHADOW` candidate. The no-corpus CLI uses a separate deterministic, non-trained shadow placeholder with no outcomes, training cycles, experimental units or adjustments; this placeholder cannot be activated. Normal training still requires at least two distinct verified experimental units. Evaluation requires independent holdout inputs and a preregistered threshold. A passing report still needs a separate host-issued approval bound to the exact evaluation fingerprint before an immutable, scope-bound adjustment overlay can be created.
- The accepted source/CI materials do not contain a verified downstream-outcome corpus or independent holdout dataset. R2 therefore does **not** claim empirical adaptation superiority. Its deterministic evidence CLI deliberately emits `NO_VERIFIED_OUTCOME_CORPUS`, zero outcomes, zero holdout scenarios, `SHADOW`, `DISABLED`, and `baselinePreserved=true`.

This is a successful fail-closed implementation state, not an experimental win and not production-deployment certification.

## 2. B2 — typed recovery procedure drafts

`RecoveryProcedureDraftService` accepts a request associated with an existing B2 impasse case. It checks that the case is resolved and that the parent scope, request identity, working-memory version and objective fingerprint still match. It requires live verified evidence and resolves every requested action ID through `ActionRegistry`.

A draft stores:

- stable draft, execution and parent-case identities;
- scope, parent request and working-memory version;
- objective and source fingerprints;
- a typed condition key and precondition/postcondition/failure-condition keys;
- registered action ID, declared capability/effect and action-descriptor fingerprint;
- evidence references, version, status and expiry.

It does not store free-form model-generated instructions or action arguments. Narrative findings remain evidence references only. The descriptor fingerprint is revalidated before approval. Draft creation is idempotent for an identical source and fails closed if the same draft ID is reused for a different proposal.

Approval is a separate review-state transition. It rechecks current parent state, evidence freshness and registered action descriptors, then requires the host-supplied `RecoveryProcedureApprovalAuthority`. The approved draft is still not a dispatchable command. A future execution host must independently resolve the registered capability, recheck live preconditions and obtain the action-specific authorization required by the application lifecycle.

PostgreSQL V011 stores the current typed JSONB payload with optimistic version CAS and a versioned transition-history table. The repository implementation exposes append-only history semantics; database superusers remain outside that API boundary.

## 3. B3 — durable evidence, ephemeral workspace

`WorkspaceBroadcastService` can be composed with a `WorkspaceReceiptRecorder`. Receipt persistence records the scope, cycle, workspace, consumer, status, consumed candidate IDs/fingerprints, workspace fingerprint, admission-policy version and time.

An outcome report is accepted only when:

1. its receipt exists in the same scope;
2. the receipt is `ACCEPTED`;
3. the candidate was actually consumed and its fingerprint matches the receipt;
4. the reported time is not earlier than the receipt;
5. the configured verifier validates the supplied evidence.

The persisted outcome captures the receipt, candidate, cycle, consumer, policy version, verifier identity, evidence references and verification time. Read-time training retrieval rechecks the receipt links and reruns the configured verifier before minting `VerifiedDownstreamOutcomeEvidence`. The wrapper has a package-private constructor, preventing other packages from labelling arbitrary records as verified.

The active `WorkspaceSnapshot` remains cycle-bounded. R2 persists receipts and outcome evidence—not live workspace state, working-memory snapshots or an indefinite broadcast workspace. An accepted receipt is not interpreted as usefulness without a separately verified downstream outcome.

PostgreSQL V011 adds `b3_workspace_consumer_receipt` and `b3_downstream_outcome` with scope-qualified primary keys, receipt foreign keys, uniqueness/idempotency constraints and candidate/time indexes. The integration test exercises repository replacement, data reload, exact replay, conflicting idempotency-key reuse and scope isolation.

## 4. Full cognitive-cycle integration

R2's services are now connected through the actual compiled cognitive-cycle graph composition in `FullGraphEvaluationHost`, rather than existing only as separately instantiated services:

- The host accepts an explicit `R2WorkflowConfiguration`; when present, it creates one `B3OutcomeEvidenceService` and injects that exact instance as the `WorkspaceReceiptRecorder` used by `WorkspaceBroadcastService`. Every accepted graph broadcast therefore persists its receipt through the configured in-memory or PostgreSQL repository. A downstream outcome is still submitted separately through the returned evidence service because the graph cannot infer whether a consumer's use was useful or harmful.
- The same `CandidateOutcomeAdjustment` instance derived from the explicitly authorized active policy is injected into the exact `B1RetrievalSelector` used by `CognitiveCycleDecisionServices.retrieveEvidence` and the exact `WorkspaceAdmissionService` used by `prepareWorkspace`. The unconfigured path remains neutral.
- The host creates one `RecoveryProcedureDraftService` over the same `ImpasseResolutionService` and action registry used by the graph lifecycle. `CognitiveCycleLifecycleServices.validateImpasseReturn` creates a typed draft only after the B2 child return is resolved and the current parent snapshot is revalidated; the resulting draft ID is carried into parent graph state. Draft creation remains separate from approval and execution.
- `FullGraphRuntime` returns the exact service instances connected to the graph for downstream evidence submission, inspection and evaluation. The evaluation host binds a decision-port fixture to the exact `WorkingMemoryService` instance used by the lifecycle and B1 projection; its planning adapter loads the live snapshot by the graph's working-memory ID and rejects any scope or version mismatch. This ensures the planner receives the same active version after selected episodic references have been projected, rather than a reconstructed fixture snapshot. The full-graph integration tests seed verified episodic memories, execute the graph, verify the projected references are present in the planning snapshot, verify B1 baseline ranking and B3 baseline admission, submit receipt-bound outcomes, train a candidate, and then compile a second full graph with the explicitly approved scoped overlay. The second run asserts that the real B1 retrieval and B3 admission paths both consume that same overlay.
- The integration test's 12 paired holdout records and utility values are deliberately synthetic, held in a test-only in-memory run ledger. They exercise the independent-unit check, paired-source verifier contract, exact version binding and authorized graph injection. They are **not** external evaluation runs, are not part of the R2 outcome corpus, and are not evidence that adaptation improves performance.

The test host uses deterministic in-memory repositories and a test-only registered read action. An application host must inject durable R2 repositories and trusted verifiers for its environment; the graph composition does not manufacture verifier identity or approval authority.

## 4. Guarded feedback and activation

`OutcomeFeedbackPolicyTrainer` accepts only B3-minted `VerifiedDownstreamOutcomeEvidence`, rejects mixed scopes, future-dated records and duplicate idempotency keys, and produces bounded per-candidate adjustments. The ordinary `train` path requires at least two distinct verified experimental-unit fingerprints. A separate `createNoEvidenceShadowCandidate` path exists solely for truthful zero-corpus reporting; it has no training outcomes, cycles, units or adjustments. `FeedbackPolicyActivationService` independently rejects candidates lacking two distinct verified training units, even if an otherwise eligible holdout report and approval are presented. The trainer cannot activate a policy.

`FeedbackPolicyHoldoutEvaluator`:

- treats a SHA-256 fingerprint of immutable scenario inputs as the common experimental unit;
- rejects duplicate display scenario IDs, duplicate experimental-unit fingerprints and any overlap with the candidate's training experimental-unit fingerprints;
- rejects reused paired source-run fingerprints and requires a trusted `FeedbackEvaluationEvidenceVerifier` to resolve and authenticate both baseline/candidate runs against the same immutable input unit and the exact baseline/candidate policy versions;
- computes paired mean utility difference, a 95% lower confidence bound, safety-regression count and mean resource delta;
- returns `SHADOW` below the preregistered minimum sample size;
- rejects candidates exceeding safety/resource limits or failing the utility lower-bound criterion;
- binds the evaluation fingerprint to candidate ID/version, scope, baseline version, source fingerprint, adjustment payload, training cycles and experimental-unit fingerprints, protocol threshold and ordered holdout observations.

The R2 reference protocol is `m8-r2-preregistered-holdout-v1`: at least 12 independent holdout scenarios, lower 95% confidence bound for mean utility gain at least 0.10, zero safety regressions and no mean resource-cost increase. A protocol change must receive a new ID and be reviewed before results can be treated as comparable. Hosts must authorize only a protocol and evidence source they trust.

Even an eligible report does not activate a candidate. `FeedbackPolicyActivationService` requires a separate approval bound to the exact scope, candidate/version and evaluation fingerprint, and passes the full candidate and full report to `FeedbackPolicyActivationAuthority` for host-side verification. `ActiveOutcomeFeedbackPolicy` is immutable and scope-bound; it returns a zero adjustment for any other scope.

`B1RetrievalSelector` and `WorkspaceAdmissionService` retain neutral adjustments in their existing constructors. Feedback is opt-in via explicit injection of the active policy. The mechanism integration test verifies that an authorized overlay can affect both B1 and B3 rankings for its own scope, while a different scope retains baseline ordering.

## 6. Empirical and ablation evidence boundary

CI regenerates the registered M8-B3 raw and paired mechanism-ablation JSONL artifacts from `M8AblationCli`. Those outputs preserve the previous M8-B3 observations and do not rewrite them. The R2 B1/B3 overlay path is additionally covered by a deterministic mechanism integration test.

There is no accepted production outcome corpus or independent holdout dataset in the source and evidence inputs available for this increment. Therefore:

- the R2 CLI is an evidence-state/protocol-integrity artifact, not an empirical benchmark;
- synthetic unit-test observations are protocol tests, not observed user or operational outcomes;
- no policy is reported as superior, promoted or activated;
- the earlier B1/top-k result remains unchanged and is not tuned away;
- any future attempt to use real outcomes must version and freeze its dataset, source provenance, scenario split, evaluator, thresholds and baseline before examining holdout results.

## 7. Hosted validation and artifacts

The active and mirrored GitHub Actions workflows run the same steps. In addition to the complete Maven/PostgreSQL `clean install`, the R2 acceptance run:

- executes the architecture/inventory guard and R2-specific security/semantics guard;
- generates M8-B2 comparison outputs and their existing source/run provenance;
- regenerates M8-B3 ablation raw/paired outputs and provenance/checksums;
- generates the deterministic R2 outcome-feedback status JSON and conclusion with source/run provenance and checksums;
- runs CodeQL Java/Kotlin source analysis and Trivy filesystem vulnerability/secret/misconfiguration/license analysis, retaining SARIF and tool-version evidence as workflow artifacts;
- retains Surefire reports, dependency tree, M8-B2/B3/R2 JSONL/JSON evidence and security outputs.

Trivy findings are intentionally reported without making the scanner's exit code a blind build gate. The findings must be reviewed and dispositioned in the hosted artifact before R2 acceptance. This does not replace independent penetration testing or a threat-model signoff.

## 8. Validation status at package authoring

Completed locally in the authoring environment for the R2-02 CI correction:

- Java 21 `javac --release 21 -Xlint:all` compilation of 346 production sources across domain, memory, core, simulator, actions, mechanisms and planning.
- All nine mechanism test classes executed with an assertion-capable local JUnit-compatible runner: 95 passed, zero failed. This includes the no-evidence-candidate activation-denial regression.
- `M8R2OutcomeFeedbackEvidenceCli` compiled and its deterministic zero-corpus test executed: 1 passed.
- Java syntax parsing passed for all seven changed Java source/test files.
- The architecture verifier and R2 semantics/evidence verifier pass, including the updated 448-production/69-test-source/407-annotation inventory.

Not executed locally:

- the complete Maven reactor or actual Surefire provider;
- the changed full-graph LangGraph4j scenarios, because the Apache Maven distribution and external framework dependencies could not be retrieved in the authoring environment;
- live PostgreSQL integration tests after the R2-02 correction;
- CodeQL/Trivy scans and scan finding triage.

The Maven wrapper attempted both configured Apache distribution hosts but DNS resolution failed. The previous hosted run did compile all twelve modules and execute the PostgreSQL integration tests; it then exposed three evaluation full-graph stale-working-memory errors and one empty-corpus CLI error. The current changes address those four failures. A new hosted CI run is required before the integration increment can be accepted; no green status is claimed in advance.

## 9. Remaining release gates

Production ERP/WMS/supplier/carrier integrations, production IAM/tenant provisioning, live verifier identity/key management, operational leases and recovery procedures, WAL/PITR and RPO/RTO validation, retention/legal hold, independent threat-model signoff, penetration testing and end-to-end post-effect production process-restart validation remain separate release gates. The R1-DEF green workflow proves only its stated deterministic adapter/test boundaries.
