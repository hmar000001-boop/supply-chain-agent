# M8-PreB7 R1-DEF — Shared Budget Authority, Reservation Recovery and Full-Graph Restart

**Delivery status:** Implementation and focused review complete; complete hosted Maven/PostgreSQL CI acceptance is pending.  
**Baseline:** User-confirmed green R1-C source line, including predecessor-scoped uncertainty idempotency.  
**Delivery type:** Cumulative change-only ZIP with complete source-file replacements; not a Git patch and not a repository archive.  
**Workstreams:** R1-D, R1-E and R1-F are integrated into one delivery and one CI acceptance gate.

## Apply

1. Start from the accepted green R1-C checkout.
2. Extract this ZIP at the repository root, preserving paths and allowing the 33 modified files to overwrite their existing versions. The 10 added repository files will be created.
3. Review `M8_PREB7_R1DEF_CHANGE_MANIFEST.csv`.
4. From the repository root, verify the repository-file checksums with:

   ```bash
   sha256sum -c M8_PREB7_R1DEF_SHA256SUMS.txt
   ```

5. Commit and push the combined change.
6. Run the complete hosted CI command:

   ```bash
   ./mvnw -B -ntp -Dphase0.postgres=true clean install
   ```

R1-DEF is not accepted until the full Java 21/PostgreSQL workflow is green on the commit containing this delivery.

## Included implementation

### R1-D — One shared durable budget authority

- The application graph exposes strict full-composition boundaries that require the exact same `ResourceBudgetExecutionGuard` instance across M5 planning and B6 lifecycle control.
- The lifecycle composition validates that M3 external `ActionDispatcher` execution and every registered `ReasoningAction` use that exact guard; validation is repeated at the authorization boundary after the mutable action registry may have changed.
- External M3 handler dispatches consume the new `ACTION_DISPATCHES` budget unit through the shared guard.
- Stable B6 reservation identities mirror the logical M3 idempotency tuple so replacing the M3 ledger cannot accidentally make an already committed logical action appear new to B6.
- Post-effect B6 accounting or M3 receipt failures are recorded as `UNKNOWN_OUTCOME`; the action is not represented as a safe authorization failure or automatically retried.

### R1-E — Fenced PostgreSQL reservations and crash recovery

- Adds the forward-only V010 migration for reservation owner, lease expiry, fencing token, operation-start marker and recovery outcome.
- Adds a leased/fencing-capable B6 ledger contract, durable PostgreSQL implementation, process owner identities, lease heartbeats and monotonic fencing tokens.
- An expired reservation with no operation-start marker can be released and reacquired under a higher fence. An explicitly released reservation is terminal and is not automatically reclaimed.
- A started operation with unknown outcome is never replayed. The ledger conservatively charges remaining capacity for each finite resource dimension, preserving reservations belonging to other active owners.
- Unstarted expiries are recovered before started unknown work is charged. PostgreSQL and in-memory ledgers have ordering regression coverage.
- V001-V009 are unchanged. V010 is the only migration added.

### R1-F — Complete application graph checkpoint restart

- Adds a fresh-JVM test that compiles the complete `compileFull` application graph, pauses at a pre-domain-effect clarification boundary, abruptly halts the first process, then reconstructs the graph in a second JVM and resumes the PostgreSQL checkpoint.
- The resumed graph verifies its terminal state and that the M5 reservation is committed in the durable PostgreSQL B6 ledger.
- Separate PostgreSQL forked-JVM tests exercise abrupt death immediately before and after the B6 operation-start marker, proving the different recovery behavior for unstarted and started work.

## Review and validation performed in this authoring environment

The following checks passed:

- M8 architecture and dependency-boundary verifier: 11 modules, acyclic internal dependency graph.
- Source/test inventory verifier: **409 production Java files; 64 test Java sources; 377 test annotations/factories**.
- Java 21 focused production compile: **319 Java files compiled successfully** across the affected modules and key PostgreSQL/runtime sources.
- Focused action dispatch regression harness: **4 methods passed** with assertion-backed local JUnit stubs. This is not represented as a Maven/JUnit run.
- V010 Java migration parser/registry probe: **3 SQL statements; migration version 10; 10 registered migrations**.
- V001-V009 byte-for-byte comparison against the accepted green R1-C baseline: pass.
- Twelve Maven POM XML files parsed successfully.
- Active and mirrored CI workflows are byte-identical.
- PostgreSQL restore script passed `bash -n`; architecture verifier passed Python syntax compilation.
- Changed Java sources passed delimiter/string/comment and trailing-whitespace checks.
- ZIP file list, per-file manifest and SHA-256 checksums were verified at packaging time.

**Not run here:** the complete Maven reactor, JUnit/Surefire suite, live PostgreSQL migration/integration tests and forked-JVM integration tests. This environment cannot run the repository's full Maven build. Those remain the required hosted CI gate; no claim of full-suite success is made by this delivery.

## Evidence boundary and remaining release gates

The full application graph restart test uses deterministic test adapters. It demonstrates fresh-JVM restoration of the complete graph checkpoint at a safe clarification boundary, not complete production durability for every memory, intention, approval, IAM, observation or business adapter. The separate B6 PostgreSQL tests cover crash recovery on both sides of the operation-start marker. This is not proof of arbitrary full-graph post-effect crash recovery.

R1-DEF does not claim:

- calibrated uncertainty probabilities (R1-C assessments remain `NOT_CALIBRATED`);
- authoritative provider token-level usage where providers do not expose it;
- a finite upper bound for unknown work in resource dimensions configured as unbounded (`Long.MAX_VALUE`);
- live ERP/WMS/supplier/carrier integrations or production IAM;
- measured WAL/PITR, RPO/RTO, tenant reconciliation or retention/legal-hold operations;
- independent threat-model signoff, SAST/SCA triage beyond the current checks, or penetration testing.

A green hosted CI run accepts the implemented contracts and the exact tested integration boundaries; it does not certify production deployment readiness.

## Next grouped delivery

The next planned increment is one larger **R2 — Outcome Feedback, Guarded Adaptation and Final Evidence Validation** package. It groups the former R2/R3/R4 workstreams into one cumulative ZIP and one full CI gate, retaining internal traceability for typed B2 recovery drafts, durable B3 outcome receipts, B1/B3 offline and held-out policy evaluation, integrated ablations, security checks, conformance refresh and final evidence/source freeze. Adaptive policies remain inactive unless they pass their pre-registered held-out criteria.
