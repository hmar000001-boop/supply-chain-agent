# M8-PreB7 R1-A — CoALA retrieval-to-working-memory reference projection

**Status:** Implemented in source; CI validation pending  
**Baseline:** Accepted M8-B6 frozen source plus the R0 conformance-baseline corrections  
**Change scope:** Runtime integration, evaluation-host composition, targeted tests and conformance documentation

## 1. Objective

CoALA treats retrieval as an internal action that reads long-term memory into the active working-memory context. Before R1-A, this application performed M2 retrieval, B1 selection and evidence-set persistence, then exposed information through the B3 workspace path; it did not project the selected long-term memory references into the versioned `WorkingMemorySnapshot`.

R1-A adds an explicit reference-based projection. It does not duplicate the full source records or replace their authoritative repositories.

## 2. Runtime contract

After M2 retrieval and B1 selection, and after the domain adapter has persisted the evidence-set record, `CognitiveCycleDecisionServices` projects only B1-selected non-working-memory candidates into the active working-memory slot:

`coala.retrieval.selected.references`

Each selected reference uses the existing B1 reference format (`kind:itemId@sourceVersion`). The `WorkingMemoryEntry` also carries `EvidenceReference("coala-selected-memory", reference)`. A separate projection marker records the graph execution identity and the base working-memory version; it is part of the slot value so an exact replay can be distinguished from an unrelated concurrent write.

The authoritative episodic, semantic or procedural record remains the source of its full provenance, validity and payload. A consumer resolves the scoped reference through the appropriate memory repository when it needs the source record. Working memory owns the active reference set, not a second copy of source truth.

B3 remains separate. A B1-selected reference in working memory is not automatically a B3-admitted or broadcast item. The B3 workspace still applies its cycle-bounded capacity and consumer-acknowledgement rules.

## 3. Safety and consistency conditions

- The selected B1 candidate must be exactly one of the candidates produced from the current M2 result.
- Candidate scope and active working-memory scope must match the decision scope.
- The working-memory identity must be present when any long-term reference is selected.
- Expired working memory cannot be revived.
- The write uses `WorkingMemoryService.put` with the graph's expected version and repository compare-and-set semantics.
- A version conflict is not merged. It returns `EvidenceDisposition.FAILED` and leaves the concurrent snapshot untouched.
- An exact replay is idempotent only when the current version is exactly the expected version plus one, the projection marker and reference/evidence set match, and the slot timestamp matches the operation time. Later writes are not treated as an idempotent replay.
- If the B1 selection contains no long-term candidates and no prior projection slot exists, no working-memory version is needlessly incremented. A previously populated projection slot is cleared by writing an empty reference list under the same version check.
- Only `EvidenceDisposition.READY` is projected. Non-ready evidence dispositions do not alter the projection slot.

The graph node copies the returned working-memory identity/version into graph state so subsequent planning must use the post-projection version. The evaluation full-graph composition injects the same `WorkingMemoryService` instance into decision and lifecycle services, backed by the same repository.

## 4. Source files changed

- `CognitiveCycleDecisionServices`: validates B1-selected candidates and performs scoped, versioned, replay-safe reference projection.
- `CognitiveCycleOperations.EvidenceRetrieved`: carries the resulting working-memory identity/version; retains the four-argument compatibility constructor for existing adapters.
- `CognitiveCycleDecisionPort`: documents idempotent evidence-set persistence across graph replay.
- `CognitiveCycleGraphTest`: checks that a returned working-memory version is propagated into graph state for subsequent operations.
- `CognitiveCycleGraph`: updates graph working-memory identity/version when retrieval reports a projection result.
- `FullGraphEvaluationHost`: shares one `WorkingMemoryService` instance between decision and lifecycle composition.
- `CognitiveCycleDecisionServicesTest`: adds focused integration tests for successful semantic-reference projection, exact replay after graph-checkpoint interruption, and stale-version conflict.

## 5. Validation state

The added tests are source changes and have **not** been executed in this authoring environment. The Maven Wrapper requires Apache Maven 3.9.16; the attempt to bootstrap it failed because `archive.apache.org` and `downloads.apache.org` could not be resolved from this environment. No Maven/JUnit pass is claimed. The patch must be applied and validated by the repository's GitHub Actions CI before R1-A is accepted.

## 6. Explicit limitations

R1-A is a reference projection, not a complete automatic retrieval/learning pipeline. It does not implement B2 reusable recovery-procedure compilation, B3 durable downstream-outcome learning, B1/B3 adaptive policy updates, cross-stage calibrated uncertainty, reservation lease/reconciliation, or full production-graph hard-kill recovery. Those remain later Pre-B7 work items.
