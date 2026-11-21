# Validation report — GAP-01–GAP-05 CI Fix 2

**Date:** 2026-11-21
**Status:** Targeted source checks and static guards passed; hosted full Maven CI pending.

## CI failure addressed
The preceding hosted run passed the runtime (87 tests) and PostgreSQL infrastructure (57 tests) modules. Fourteen evaluation scenarios then errored at `CognitiveCycleDecisionServices.selectedCandidate` with planner identity/session provenance mismatch.

The root cause was B4 active-plan binding: the intention version advanced, and the graph incorrectly cleared the planner session even though the authoritative method and objective were unchanged. Authorization retained the candidate-set ID but no longer had the matching session reference.

## Changes
- Preserve the planner session for version-only B4 active-plan binding.
- Continue to invalidate it when intention identity, method ID, method candidate reference or objective fingerprint changes.
- Explicit `RECONSIDER` and `CHANGE_METHOD` actions always invalidate the prior session.
- Keep load-context's version-sensitive external/concurrent-change invalidation.
- Keep candidate-set provenance validation fail-closed; separate planner-ID and session-ID error messages.
- Add a full-graph regression assertion that session identity is retained at the approval breakpoint.

## Local checks
| Check | Result |
|---|---|
| `scripts/verify-m8-b5-architecture.py` | PASS |
| `scripts/verify-m8-r2-evidence.py` | PASS |
| `scripts/verify-m8-preb7-f03-f04.py` | PASS |
| Targeted source invariant checks | PASS |
| Changeset file and archive integrity | PASS |

## Not run
The Maven wrapper could not obtain Maven 3.9.16 because the runtime environment had no DNS/network access to the configured Apache distribution hosts. Therefore the full Maven/Surefire reactor, live PostgreSQL tests and the new full-graph regression assertion have **not** been executed locally. The exact hosted CI run is required to establish acceptance.

## Scope
Four project paths: three modified Java source files and one added research/validation note. Zero deletions. No POM, dependency, schema or workflow changes.
