# M8-PreB7 R2 — Outcome Feedback, Guarded Adaptation and Final Evidence Validation

**Status:** Implemented; awaiting one complete hosted Java 21/PostgreSQL CI acceptance cycle and scan-artifact review.  
**Baseline:** User-confirmed green R1-DEF source state, 2026-11-20.  
**Delivery shape:** One cumulative change-only ZIP; B2, B3, feedback, evaluation, security and conformance work are internal boundaries, not separate acceptance increments.

## 1. Executive result

R2 adds the missing typed and durable edges required to make future outcome-driven adaptation possible without activating an unproven policy:

- B2 resolves procedure drafts only from resolved child cases, current parent state, verified evidence and registered action descriptors. The draft service contains no execution or dispatch method. An approval moves only the review status to `APPROVED`; it is not action authorization.
- B3 persists accepted consumer receipts and separately verified downstream outcomes. A receipt is not itself a positive outcome. Persisted outcome rows are re-linked to the original accepted receipt and their proof is re-verified before the B3 service mints a package-private verified-evidence capability for training.
- B1/B3 feedback is opt-in, scope-bound and neutral by default. Training produces a `SHADOW` candidate. Evaluation requires independent holdout scenario IDs and a preregistered threshold. A passing report still needs a separate host-issued approval bound to the exact evaluation fingerprint before an immutable, scope-bound adjustment overlay can be created.
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

## 4. Guarded feedback and activation

`OutcomeFeedbackPolicyTrainer` accepts only B3-minted `VerifiedDownstreamOutcomeEvidence`, rejects mixed scopes, future-dated records and duplicate idempotency keys, and produces bounded per-candidate adjustments. It cannot activate a policy.

`FeedbackPolicyHoldoutEvaluator`:

- treats the scenario as the holdout experimental unit;
- rejects duplicate holdout IDs and any overlap with training cycle IDs;
- computes paired mean utility difference, a 95% lower confidence bound, safety-regression count and mean resource delta;
- returns `SHADOW` below the preregistered minimum sample size;
- rejects candidates exceeding safety/resource limits or failing the utility lower-bound criterion;
- binds the evaluation fingerprint to candidate ID/version, scope, baseline version, source fingerprint, adjustment payload, training cycles, protocol threshold and ordered holdout observations.

The R2 reference protocol is `m8-r2-preregistered-holdout-v1`: at least 12 independent holdout scenarios, lower 95% confidence bound for mean utility gain at least 0.10, zero safety regressions and no mean resource-cost increase. A protocol change must receive a new ID and be reviewed before results can be treated as comparable. Hosts must authorize only a protocol and evidence source they trust.

Even an eligible report does not activate a candidate. `FeedbackPolicyActivationService` requires a separate approval bound to the exact scope, candidate/version and evaluation fingerprint, and passes the full candidate and full report to `FeedbackPolicyActivationAuthority` for host-side verification. `ActiveOutcomeFeedbackPolicy` is immutable and scope-bound; it returns a zero adjustment for any other scope.

`B1RetrievalSelector` and `WorkspaceAdmissionService` retain neutral adjustments in their existing constructors. Feedback is opt-in via explicit injection of the active policy. The mechanism integration test verifies that an authorized overlay can affect both B1 and B3 rankings for its own scope, while a different scope retains baseline ordering.

## 5. Empirical and ablation evidence boundary

CI regenerates the registered M8-B3 raw and paired mechanism-ablation JSONL artifacts from `M8AblationCli`. Those outputs preserve the previous M8-B3 observations and do not rewrite them. The R2 B1/B3 overlay path is additionally covered by a deterministic mechanism integration test.

There is no accepted production outcome corpus or independent holdout dataset in the source and evidence inputs available for this increment. Therefore:

- the R2 CLI is an evidence-state/protocol-integrity artifact, not an empirical benchmark;
- synthetic unit-test observations are protocol tests, not observed user or operational outcomes;
- no policy is reported as superior, promoted or activated;
- the earlier B1/top-k result remains unchanged and is not tuned away;
- any future attempt to use real outcomes must version and freeze its dataset, source provenance, scenario split, evaluator, thresholds and baseline before examining holdout results.

## 6. Hosted validation and artifacts

The active and mirrored GitHub Actions workflows run the same steps. In addition to the complete Maven/PostgreSQL `clean install`, the R2 acceptance run:

- executes the architecture/inventory guard and R2-specific security/semantics guard;
- generates M8-B2 comparison outputs and their existing source/run provenance;
- regenerates M8-B3 ablation raw/paired outputs and provenance/checksums;
- generates the deterministic R2 outcome-feedback status JSON and conclusion with source/run provenance and checksums;
- runs CodeQL Java/Kotlin source analysis and Trivy filesystem vulnerability/secret/misconfiguration/license analysis, retaining SARIF and tool-version evidence as workflow artifacts;
- retains Surefire reports, dependency tree, M8-B2/B3/R2 JSONL/JSON evidence and security outputs.

Trivy findings are intentionally reported without making the scanner's exit code a blind build gate. The findings must be reviewed and dispositioned in the hosted artifact before R2 acceptance. This does not replace independent penetration testing or a threat-model signoff.

## 7. Validation status at package authoring

Completed locally in the authoring environment:

- Java 21 `javac --release 21 -Xlint:all` compile for the domain, memory, simulator, actions and mechanisms production source sets (290 files).
- Compile of all mechanisms test sources.
- 21 focused B2/B3/feedback tests passed with an assertion-capable local reflection runner using JUnit API stubs.
- Java 21 compile of the new PostgreSQL B2/B3 repositories and migration runner.
- Compile of the new PostgreSQL integration-test source.
- Seven PostgreSQL migration-runner parsing tests passed, including V011 registration and six-statement parsing.
- Compile and one passing deterministic test for `M8R2OutcomeFeedbackEvidenceCli`.
- Static source inventory and the R2 guard script (must pass in CI).

Not executed locally:

- the complete Maven reactor or Surefire provider;
- live PostgreSQL integration tests against a database;
- CodeQL/Trivy scans and scan finding triage.

The authoring environment has no installed Maven distribution and cannot retrieve the wrapper distribution. The single hosted CI run is therefore the acceptance gate for Maven, live PostgreSQL and security evidence. The implementation is not described as CI-green until the project owner confirms that run and the scan artifacts have been reviewed.

## 8. Remaining release gates

Production ERP/WMS/supplier/carrier integrations, production IAM/tenant provisioning, live verifier identity/key management, operational leases and recovery procedures, WAL/PITR and RPO/RTO validation, retention/legal hold, independent threat-model signoff, penetration testing and end-to-end post-effect production process-restart validation remain separate release gates. The R1-DEF green workflow proves only its stated deterministic adapter/test boundaries.
