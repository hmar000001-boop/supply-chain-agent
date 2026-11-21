# F-01 / F-02 implementation changeset

**Base source archive:** `supply-chain-agent-main (6).zip`  
**Base SHA-256:** `ea765285e4a20520bd51cf49abaa02dedc8382015fce325862b4ab86dc1894a6`  
**Scope:** F-01 B4 planner-method authority and F-02 B6 next-planning-call compute downshift.  
**Explicitly deferred:** F-03 (B2 recovery draft promotion/reuse) and F-04 (B3 alternative coalition competition) are not modified.

## Apply

Extract this ZIP at the repository root, on top of the exact base archive above. The archive contains repository-root-relative paths and complete replacements for every modified source/document file, plus complete new source files. It does not contain an outer wrapper directory.

Then run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

## F-01 — B4 method controls the next planner

- Introduces a typed `PlannerSelectionOverride` bound to the persisted intention ID/version, method reference and objective SHA-256 fingerprint.
- Resolves supported B4 method IDs to registered M5 planners; unknown identifiers fail closed. Hosts can inject a domain-specific `PlannerMethodResolver`.
- `PlannerRouter` honours the verified override before the generic complexity policy. The planner still produces only a proposal; it receives no execution authority.
- A method/objective/authority binding change clears the previous planner-session reference so a session from an old method version is not resumed under new authority.
- The objective fingerprint must match both the persisted intention and the actual planning request.

## F-02 — B6 downshifts the next planning call

- `REDUCE_COMPUTATION` carries a typed `PlannerBudgetDownshift` directive.
- The graph applies a bounded cumulative scale to the next `PlannerBudget` (expansions, evaluations, model calls, rollouts and call-local runtime).
- Structural search depth, result cardinality and the absolute deadline are preserved.
- The shared execution-wide `ResourceBudget` remains unchanged and continues to be enforced by the common reservation/lease guard.
- If no further consumable reduction is possible, the graph refuses another planner invocation rather than pretending the downshift succeeded.
- Planner reservation identity now includes planning round and effective per-call budget envelope, preserving checkpoint retry idempotency while avoiding collisions between distinct downshifted planning attempts.

## Validation boundary

The accompanying `VALIDATION_REPORT.md` lists the checks actually run. The full Maven reactor, Surefire with the real dependencies, and the hosted PostgreSQL integration tests have **not** been run locally. GitHub Actions remains the authoritative validation of this integrated changeset.
