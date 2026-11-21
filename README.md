# GAP-01–GAP-05 CI Fix 2 — B4 planner-session preservation

Apply this repository-root-relative changeset **after** the GAP-01–GAP-05 implementation ZIP and CI Fix 1 (initial B6 assessment ordering). Extract at the repository root, replacing the listed files.

## Root cause
The B4 lifecycle increments the intention version when it binds the selected active plan. The graph previously treated that version-only transition as a planner-authority change and cleared the planner session before authorization resolved the candidate set.

## Correction
A version-only active-plan binding preserves the current planner session. A changed intention identity, method ID, method candidate reference, objective fingerprint, explicit reconsideration, or explicit method change still invalidates it. The candidate-set identity/session validation remains fail-closed.

## Included paths
- `supply-chain-agent-runtime/src/main/java/dev/supplychain/agent/runtime/cognitive/CognitiveCycleGraph.java`
- `supply-chain-agent-runtime/src/main/java/dev/supplychain/agent/runtime/cognitive/decision/CognitiveCycleDecisionServices.java`
- `supply-chain-agent-evaluation/src/test/java/dev/supplychain/agent/evaluation/M8B4FullGraphWorkflowTest.java`
- `docs/research/GAP01-GAP05-CI-FIX2-B4-PLANNER-SESSION-PRESERVATION.md`

No files are deleted. No POMs, dependency versions, schema migrations, or CI workflows are changed.

## Validation
- Architecture guard: PASS
- R2 evidence guard: PASS
- F-03/F-04 guard: PASS
- Targeted source invariant checks: PASS
- ZIP integrity and SHA-256: verified
- Full Maven/Surefire and live PostgreSQL: not run locally; hosted CI is the acceptance check.

## Next command
```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```
