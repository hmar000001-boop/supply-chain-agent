# M8 pre-B7 R2-02 CI Fix 3 — B2 routing and parent-context bridge

This is a **changes-only source replacement package**. Extract it at the repository root, on top of the accepted R1-DEF baseline and the R2 / R2-02 packages, including R2-02 CI Fix 1 and CI Fix 2.

## What it corrects

1. **The full cognitive-cycle graph now routes `NO_FEASIBLE_CANDIDATE` into B2 resolution.** The evaluation service already emits this disposition when the candidate set is empty or all candidates are infeasible; previously the conditional edge handled only `IMPASSE`, so this disposition incorrectly went straight to escalation without running the integrated B2 child graph.
2. **The B2 child receives the parent context it needs to carry out and validate the investigation.** The bridge now preserves the active working-memory ID/version, intention ID/version, evidence and workspace references, planner/session and candidate-set references, selected candidate reference, execution/scope identity, uncertainty-assessment reference, and impasse reason.
3. **The child boundary remains narrow.** It does not copy the parent request text, normalized intent, summary, clarification content, memory contents, candidate objects, or capabilities. B2 lifecycle services resolve the authoritative parent records through the preserved identities and their owning stores. The child still receives only the capabilities delegated into its B2 case record.
4. **The B2 attempt is bounded to one terminal case per cognitive-cycle execution.** After a resolved B2 return, the parent reloads and re-evaluates its context. If another impasse is reached in that same execution, it escalates rather than reopening the terminal execution-scoped case or entering a repeated child-resolution loop. This is an explicit engineering loop-bound policy, not a rule attributed to CoALA or Fan & Lan.

## Files

All three Java files are complete replacements at their repository-relative paths:

- `supply-chain-agent-runtime/src/main/java/dev/supplychain/agent/runtime/cognitive/CognitiveCycleGraph.java`
- `supply-chain-agent-runtime/src/main/java/dev/supplychain/agent/runtime/cognitive/B2ResolutionGraphState.java`
- `supply-chain-agent-runtime/src/main/java/dev/supplychain/agent/runtime/cognitive/B2ResolutionSubgraph.java`

No test source, migration, dependency, or configuration file is changed. The existing failing full-graph integration test is deliberately left intact: it remains the acceptance check for this integration path.

## Apply and verify

Extract this ZIP at the repository root, replacing the three listed files, then run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

The hosted CI result is authoritative for the complete reactor and integration-test behavior. See `VALIDATION_REPORT.md` for the exact local checks performed and their limits.
