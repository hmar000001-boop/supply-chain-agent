# M8-PreB7 R1-C CI Fix 6 — predecessor-scoped uncertainty idempotency

## Purpose

Fixes the full-graph re-planning failures caused by reusing a logical uncertainty trigger after its predecessor assessment revision has advanced.

The lifecycle service now derives an opaque deterministic request idempotency key from:

- the logical graph trigger;
- execution ID and memory-scope ID; and
- the exact predecessor assessment reference (series ID, revision, execution and scope).

Consequently, a checkpoint retry from the same predecessor resolves to the same persisted assessment, while the same logical trigger encountered from a newer predecessor produces a distinct request key and can create the next revision. The exact `+1` revision, same-series, execution/scope and validity checks remain unchanged.

## Contents

This is a change-only ZIP containing **complete replacement files**, not a Git patch and not a complete repository archive. All source/document files are stored at their repository-relative paths.

1. `supply-chain-agent-runtime/src/main/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServices.java`
2. `supply-chain-agent-runtime/src/main/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecyclePort.java`
3. `supply-chain-agent-runtime/src/test/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServicesTest.java`
4. `docs/pre-b7/M8-PREB7-R1C-VERSIONED-UNCERTAINTY.md`
5. `scripts/verify-m8-b5-architecture.py`
6. `docs/phase8/M8-TEST-INVENTORY.csv`

## Apply

Extract the ZIP at the repository root and allow the six files to replace their existing counterparts. The ZIP has no changes to Maven dependencies, migrations, workflows, or production adapters.

## Validation

- `python scripts/verify-m8-b5-architecture.py` — passed during preparation.
- The added lifecycle regression test verifies same-trigger replay from one predecessor and a new revision from the next predecessor.
- The three existing `M8B4FullGraphWorkflowTest` scenarios are retained as end-to-end regression coverage.
- Maven tests were **not run locally**: Maven is not installed in the preparation environment and the Maven Wrapper distribution could not be downloaded because DNS/network access to the Apache distribution hosts was unavailable. The full GitHub Actions reactor run remains required before R1-C can be accepted.

## Expected source/test inventory

- 403 production Java files
- 62 test Java sources
- 358 test annotations/factories (runtime module: 73)
