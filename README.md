# B1 learned utility scale fix — M8-B5 inventory-safe replacement

## Why this replacement package exists

The previous B1 fix package added a new JUnit `@Test` method. The M8-B5 architecture verifier intentionally freezes the Mechanisms test inventory at 125 annotated methods, and the tracked inventory CSV also records 125. CI therefore correctly reported 126 actual methods versus 125 expected and recorded.

This package fixes the delivery mistake without weakening or changing the inventory guard. The regression assertions are merged into the pre-existing verified-outcome test rather than creating an additional test method.

## Exact baseline

- Repository archive: `supply-chain-agent-main (9).zip`
- Baseline archive SHA-256: `cf300670b0bca3082400b5711fa3d1db1ea9c280625720834ace0332129512f4`
- This package supersedes `B1_LEARNED_UTILITY_SCALE_FIX_CHANGESET_ONLY.zip`.
- Apply by extracting at the repository root. It also applies cleanly over the earlier B1 package because it replaces the same two paths with the corrected final contents.

## Changes — exactly two application files

1. `supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/b1/B1MemoryFeatureLearningService.java`
   - Preserves provider-supplied task-scale `expectedDownstreamUtility` during candidate enrichment.
   - Continues to enrich access/verified-use history and cue-association strengths.
   - Leaves the normalized [0,1] outcome statistic persisted separately.

2. `supply-chain-agent-mechanisms/src/test/java/dev/supplychain/agent/mechanisms/b1/B1MemoryFeatureLearningServiceTest.java`
   - Does not add a new JUnit `@Test` method.
   - Extends the pre-existing verified-outcome test with a provider utility of `12.0`, while the persisted normalized outcome statistic is `1.0`.
   - Asserts that access history and cue associations still enrich the candidate, provider utility remains `12.0`, and normalized outcome utility remains persisted as `1.0`.

## Inventory compatibility

The Mechanisms module inventory remains exactly `(166 production Java files, 12 test sources, 125 annotated test methods/factories, 12 annotated test classes, 0 helper sources)`.

No change is made to:

- `scripts/verify-m8-b5-architecture.py`
- `docs/phase8/M8-TEST-INVENTORY.csv`
- Any Maven POM, CI workflow, SQL migration, evaluator, fixture, B3 code, or other application source.

## Validation performed

- Java 21 compilation of 339 production sources across Domain, Memory, Core, Simulator, Actions and Mechanisms: PASS.
- Both methods in `B1MemoryFeatureLearningServiceTest` compiled and executed in a temporary local assertion harness: 2/2 PASS. This is not the JUnit engine.
- `python3 scripts/verify-m8-b5-architecture.py`: PASS; includes source/test inventory, module graph, framework dependency and workflow-mirror checks.
- Module inventory independently recomputed and compared with both the verifier expectation and CSV: PASS `(166, 12, 125, 12, 0)`.
- Maven/JUnit and GitHub Actions were not run in this environment; GitHub Actions remains the authoritative CI check.

## Apply

Extract this ZIP at the repository root, allowing the two Java files to overwrite their earlier versions. Do not modify the architecture verifier or inventory CSV to accommodate the previous count of 126.
