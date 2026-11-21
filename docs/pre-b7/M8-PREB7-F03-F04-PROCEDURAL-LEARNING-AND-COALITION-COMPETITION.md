# M8-PreB7 F-03/F-04 — governed recovery-procedure reuse and B3 coalition competition

**Status:** Implemented in the F-03/F-04 candidate changeset; hosted CI acceptance is pending.  
**Dependency:** Apply after the F-01/F-02 candidate changeset has passed CI.  
**Scope:** B2-to-CoALA procedural-memory lifecycle and B3 workspace-admission competition. No F-01/F-02 behavior is modified here.

## F-03 — B2 recovery drafts become governed CoALA procedural memory

### Lifecycle

A resolved B2 child can produce a typed `RecoveryProcedureDraft`. B2 approval remains an evidence-bound authorization to *stage* a reusable proposal; it does not automatically make the procedure active. `RecoveryProcedurePromotionService.stageApprovedDraft`:

1. reloads the current draft and checks the expected draft version;
2. requires `APPROVED`, exact scope, and a still-valid promotion interval;
3. constructs a deterministic typed `RecoveryProcedureArtifact` from registered action-descriptor references, opaque precondition/postcondition/failure keys, evidence references, parent/objective/source fingerprints and approval metadata;
4. re-verifies the source evidence and current registered action descriptors;
5. publishes the immutable artifact by content address; and
6. stages a CoALA `ProcedureVersion` as `DRAFT` with `HUMAN_VERIFIED` provenance and the B2 draft identity/fingerprint.

The procedural lifecycle is separate. The B2 draft approver cannot act as the procedural reviewer; the procedural reviewer cannot act as the activation approver; and the original B2 draft approver cannot activate the item. Activation still goes through `ProceduralMemoryService` and its optimistic lifecycle revision.

### Artifact and retrieval

`RecoveryProcedureArtifactCodec` uses a deterministic, bounded, versioned binary representation. It stores only allow-listed action IDs, capability/effect and descriptor fingerprints, opaque condition keys and evidence references. It does not store free-form generated instructions or executable action arguments. The PostgreSQL adapter publishes content-addressed immutable payloads and verifies the payload digest on retrieval. Forward-only migration V012 creates the artifact table; V001–V011 are not rewritten.

After activation, the shared `ProceduralMemoryRepository` exposes the version through the ordinary CoALA `MemoryRetrievalService`. `B1MemoryCandidateFactory` converts it to a procedural-memory candidate; the reference is eligible for ordinary B1 selection and the selected reference can be projected into working memory. `RecoveryProcedurePromotionService.retrieveActive` provides the governed dereference operation for an exact scope and condition key. It rechecks active status, provenance, artifact integrity, evidence validity and current action-descriptor fingerprints before returning typed planning knowledge. The service never dispatches the referenced action.

### Boundaries

- **Automatic activation:** not implemented; explicit procedural validation and activation remain mandatory.
- **Automatic execution:** not implemented; a retrieved procedure is planning knowledge, not an action grant or dispatcher command.
- **Default evaluation host:** uses in-memory CoALA procedural and recovery-artifact stores. The PostgreSQL content-addressed artifact adapter and repository-replacement integration test are included, but a durable production full-graph host composition is not claimed.
- **Failure behavior:** an artifact may be orphaned if its immutable publication succeeds but procedural-memory staging later fails. It remains unreferenced content and is safe to retry; no active procedure points to it. Staging retries are bounded and idempotent by source draft and artifact identity.

## F-04 — B3 first-class coalition competition

B3 admission now compares singleton candidate options with bounded alternative content coalitions before admitting optional content. The default search considers pairs and triples, with a hard proposal-evaluation cap per workspace. Mandatory content is protected before optional competition. Conflicting or over-capacity groups are not feasible options.

Each feasible proposal records a deterministic identity, round, sorted member IDs, newly covered tags, context cost, base score, coverage bonus and total score. A deterministic comparator selects the best bundle against singleton options; a winning multi-item bundle is persisted in the active `WorkspaceSnapshot` as a `WorkspaceCoalition` with a matching selected-proposal trace. The snapshot constructor enforces that persisted coalitions match the selected trace.

If the proposal bound is reached before a round's option set is complete, B3 does not choose from the partial coalition set. It marks the search truncated and uses singleton-only fallback for that and subsequent rounds. This avoids claiming that an incomplete search found the best coalition.

### Boundaries

- This is an explicit, bounded competition among content bundles, not a full reproduction of LIDA codelet activation, global-workspace broadcast learning or a biologically grounded cognitive architecture.
- Coalition score is a deterministic engineering policy over current candidate scores and joint coverage; the papers do not prescribe this equation.
- B3 workspace snapshots remain cycle-bounded and ephemeral. Existing consumer receipts and downstream-outcome persistence are unchanged.
- The R2 outcome-feedback overlay remains gated by independently verified outcome and holdout evidence. These changes do not activate adaptation or claim empirical superiority.

## Tests and acceptance

Focused Java 21 tests cover:

- approved B2 draft staging without auto-activation;
- independent procedural reviewer and approver;
- standard CoALA retrieval and B1 candidate formation after activation;
- stale evidence/action-descriptor and cross-scope rejection;
- deterministic artifact codec and PostgreSQL repository replacement/idempotent publication;
- competing singleton/pair/triple options, deterministic selection, conflict exclusion, search truncation fallback, and snapshot trace consistency.

The CI workflow adds `scripts/verify-m8-preb7-f03-f04.py`. The candidate package has not yet passed the hosted Maven/PostgreSQL reactor. Apply it only after F-01/F-02 acceptance; the combined stack is accepted only when the full workflow is green.
