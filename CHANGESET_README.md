# B1 learned utility scale mismatch — minimal change-only package

## Baseline

- Repository archive: `supply-chain-agent-main (9).zip`
- Baseline archive SHA-256: `cf300670b0bca3082400b5711fa3d1db1ea9c280625720834ace0332129512f4`
- Snapshot identifier embedded in the archive: `309985a6600810ba5d57d0a51aebea71591c209f`

Apply this overlay only to that exact baseline (or a repository proven to contain the same two original files).

## Exact scope

Exactly two repository files are modified:

1. `supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/b1/B1MemoryFeatureLearningService.java`
   - Candidate enrichment still applies persisted access/verified-use history and cue-association strengths.
   - It no longer replaces task-specific provider utility with the persisted normalized [0,1] outcome statistic.
   - The normalized outcome statistic remains stored; no persistence schema or migration changes are required.
2. `supply-chain-agent-mechanisms/src/test/java/dev/supplychain/agent/mechanisms/b1/B1MemoryFeatureLearningServiceTest.java`
   - Adds a regression test with provider utility `12.0` and a verified-outcome statistic of `1.0`.
   - Asserts that provider utility remains `12.0`, while verified-use history and cue-association learning still enrich the candidate.
   - Renames the pre-existing test method to reflect that it checks persisted feature statistics, not candidate utility substitution.

No B1 selector, maintenance accounting, B3 code, other mechanism, evaluator, fixture, Maven file, database migration, CI workflow, or application documentation is changed.

## Validation performed

- Java 21 direct production compilation passed for the required modules: domain (53 sources), simulator (5), memory (52), actions (59), mechanisms (166).
- The three methods in `B1MemoryFeatureLearningServiceTest` compiled and executed in a temporary local assertion harness: 3 passed. This was not the Maven/JUnit engine.
- Maven command attempted: `./mvnw -B -ntp -pl supply-chain-agent-mechanisms -am -Dtest=B1MemoryFeatureLearningServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- Maven/JUnit could not run because Maven 3.9.16 was not installed and DNS resolution failed for both configured Apache distribution hosts. GitHub Actions remains the authoritative CI verification.

## Apply

Extract the two Java files to the repository root, preserving their paths. The README and manifest are delivery metadata and are not application source files.
