# R2-02 Validation Report

## Source-level implementation verified

- B2: `CognitiveCycleLifecycleServices.validateImpasseReturn` invokes the typed recovery-draft service only after the child result resolves and the current parent scope/request/working-memory version/objective fingerprint has been revalidated. The graph carries the draft ID into parent state.
- B3: `FullGraphEvaluationHost` injects one configured `B3OutcomeEvidenceService` into `WorkspaceBroadcastService` as the receipt recorder and exposes that same service for separately submitted downstream outcomes. Persisted outcomes are revalidated before verified training wrappers are minted.
- B1/B3: the same `CandidateOutcomeAdjustment` derived from the approved active policy is injected into the actual `B1RetrievalSelector` and `WorkspaceAdmissionService` used by the compiled cognitive-cycle graph.
- Holdout: training/holdout independence uses a common immutable experiment-unit fingerprint. Paired baseline/candidate source-run fingerprints require trusted host verification against the common unit and exact baseline/candidate policy versions.
- No-corpus behavior remains neutral/SHADOW/DISABLED; the synthetic integration test is explicitly not an empirical benchmark.

## Local Java 21 validation

| Check | Result |
|---|---:|
| Mechanisms production compilation | 123 source files passed |
| Runtime production compilation | 37 source files passed |
| Application production compilation | 2 source files passed |
| PostgreSQL infrastructure production compilation | 38 source files passed |
| Evaluation production compilation | 23 source files passed |
| Mechanism test source compilation | 9 classes passed |
| Mechanism tests executed with assertion-capable focused runner | 94 passed, 0 failed |
| Runtime test source compilation | 11 classes passed |
| Evaluation test source compilation | 8 classes passed |
| R2 PostgreSQL integration test source compilation | Passed |
| Architecture verifier | Passed |
| R2 evidence verifier | Passed |
| V011 restore-verification Bash syntax | Passed |
| ZIP integrity and checksums | Passed |

Framework and test APIs unavailable to the local compilation environment were represented by API-compatible stubs for source/type checking. This is not equivalent to the real Maven compiler or JUnit Surefire execution. The fullgraph tests were compiled but not executed locally against the real LangGraph4j runtime.

## Not run locally / hosted acceptance required

- `./mvnw -B -ntp -Dphase0.postgres=true clean install` across all 12 Maven modules.
- Execution of the fullgraph B2/B3/B1 integration tests using the real LangGraph4j runtime.
- Live PostgreSQL execution of the updated R2 evidence integration test and V011 migration/repository checks.
- Backup/restore verification against a live isolated PostgreSQL target.

## Acceptance status

**IMPLEMENTED — PENDING ONE HOSTED CI ACCEPTANCE CYCLE.** Do not mark R2-02 complete until the full workflow is green. This increment is intended to close the identified educational integration gaps; it does not prove production readiness or empirical adaptive-policy superiority.
