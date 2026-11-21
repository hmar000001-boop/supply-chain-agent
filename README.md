# M8-PreB7 R2-02 CI Fix 1 — Full-Graph Working-Memory and Empty-Corpus Corrections

This is an incremental, changes-only delivery. Apply it at the repository root on top of the accepted R1-DEF baseline, the R2 initial implementation, R2 CI Fix 1 (V011 backup/restore verifier), and R2-02 full-graph integration package.

## CI failures addressed

The latest hosted run passed the first 11 Maven modules, including 54 PostgreSQL tests, but the evaluation module reported four test errors:

1. Three full-graph scenarios failed because the deterministic evaluation `DecisionAdapter` built its planning request from a fixture-created version-1 working-memory snapshot, while B1 had already projected selected episodic references into the graph's active snapshot and incremented its version. The graph correctly rejected the stale request.
2. The empty-corpus R2 evidence CLI called the normal trainer with zero evidence, even though normal training correctly requires at least two distinct verified experimental units.

## Corrections

- `FullGraphEvaluationHost` now exposes an optional evaluation-only binding contract for a decision adapter to receive the host's exact `WorkingMemoryService` instance.
- The full-graph test `DecisionAdapter` now loads the snapshot identified by the active graph context, verifies its scope and exact version, and builds the planning request from that snapshot. The integration assertion verifies the planner received the selected CoALA episodic references projected by B1.
- `OutcomeFeedbackPolicyTrainer.createNoEvidenceShadowCandidate(...)` creates a deterministic, non-trained placeholder for the no-corpus CLI path. It contains no source outcomes, training cycles, experiment units or score adjustments. The normal `train(...)` method retains its minimum-evidence requirement.
- `FeedbackPolicyActivationService` independently rejects candidates without at least two distinct verified training experiment units and at least one source outcome.
- The R2 evidence CLI now uses the no-evidence factory. Inventory, architecture/evidence verifiers, acceptance register and R2 technical note are updated.

## Application

Extract the ZIP at the repository root and overwrite the 11 matching files. Do not remove or replace any earlier R1/R2 files.

Then run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

## Scope and claims

The new graph tests deliberately use synthetic in-memory training/holdout evidence to verify the graph wiring and authorization boundary only. They are not empirical adaptive-learning results. The real outcome corpus remains empty, the production candidate remains SHADOW, activation remains DISABLED, and the baseline remains active.
