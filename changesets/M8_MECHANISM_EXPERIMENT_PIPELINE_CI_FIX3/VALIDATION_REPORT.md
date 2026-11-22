# Validation report — M8 mechanism experiment pipeline CI Fix 3

Candidate ID: `M8-GAP01-GAP05-MECHANISM-EXPERIMENT-HARNESS-V1-CI-FIX3`

Candidate code-tree SHA-256: `e9a52eff5493c0af0c9dc1912dc7aa1300c9e5c6f03f2d128e1f2c9677ec2d10`

File count: 676.

## Failure addressed

The latest hosted CI report had one runtime error: `SecurityException: matched arms share mutable workspace consumer`. The test used a stateless lambda and accidentally relied on separate ScenarioRuntime construction to yield separate lambda instances. The JVM reused the stateless lambda object. The fixture now constructs a fresh explicit consumer object per runtime; the test asserts distinct consumer references. The experiment adapter's isolation check is not weakened.

## Checks run locally

- Test-source invariant check for explicit fresh consumer and regression assertion: PASS.
- Java 21 JVM smoke showing stateless-lambda reuse and new anonymous instance identity: PASS.
- M8 architecture/source inventory guard: PASS (11 modules, 547 production Java files, 77 test sources).
- R2 evidence guard: PASS.
- F-03/F-04 guard: PASS.
- Full-graph experiment guard: PASS (22 checks).
- Scenario register guard: PASS (280 TRAIN, 280 HOLDOUT, 210 TRANSFER; disjoint seed ranges).
- Mechanism evidence guard: PASS (25 checks).
- Candidate fingerprint recomputed and recorded: PASS.
- ZIP integrity and all checksums: PASS.

The full Maven/Surefire/PostgreSQL reactor was not run locally. The exact full-graph regression test and all 12 modules must still be executed by hosted CI.

## Scope

One test source is replaced, the candidate fingerprint metadata is updated, and two research notes are added. No production Java logic or dependencies are changed. No TRAIN/HOLDOUT/TRANSFER experiment was run.
