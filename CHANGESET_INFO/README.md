# M8 effective-input provenance CI Fix 2 — changes only

## Delivery format

This archive is a repository-root-relative changeset. Extract its contents at the root of the existing repository, preserving paths. It is **not** a full repository snapshot and contains no Git patch.

- Project files: **8** (7 replacements, 1 addition)
- Deleted project files: **0**
- Maven POM/dependency changes: **0**
- Project source files changed: 5 (3 production, 2 tests)
- Candidate fingerprint: `62d1b39c75aab9a87b136a75d97c31b2371c0a396fc941581a5bbc614aa83b64`
- Candidate file count: 719

The `CHANGESET_INFO/` folder contains only the manifest, validation report, README and checksums.

## Root cause found in the hosted CI log

The run at `2026-11-22T22:56:01Z` passed the first 11 Maven modules and compiled all 106 evaluation production sources. It then failed in evaluation `testCompile` with one reported compiler error: `M8B4FullGraphWorkflowTest.java:252` calls `FullGraphExperimentRunObservation.effectiveInputFingerprint()`, but the observation record did not expose that component.

The scenario unit and matched-pair records already had this fingerprint, and the adapter already included it in the observation-fingerprint canonical input. However, the per-arm observation record and its JSONL serialization dropped it. This was an incomplete provenance-contract propagation. The pair constructor also did not assert that each arm was bound to the pair's exact effective input.

## Clean fix

- Add required `effectiveInputFingerprint` to the run-observation record.
- Serialize it as a first-class field in every `fullGraphRun` JSONL object.
- Populate it from the exact registered `ExperimentScenarioUnit` in the adapter.
- Require both matched arms to equal the pair's effective-input fingerprint.
- Expand existing regression tests for per-arm JSONL and pair mismatch rejection. No new `@Test` annotation was added, so architecture inventory counts remain unchanged.

## Apply

Extract at repository root. Then run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

Do not start TRAIN data collection based solely on this patch. The next complete hosted CI run must pass the evaluation test compilation and execution before proceeding.
