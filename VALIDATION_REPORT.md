# Validation report — M8 Full-Graph Experiment Adapter v1

## Source-level checks

All five repository guards passed:

- M8-B5 architecture and exact source/test inventory.
- M8-PreB7 R2 evidence and B1/B3 activation boundaries.
- F-03/F-04 implementation/evidence guard.
- Preregistered scenario register parity, allocation and fingerprint checks.
- Full-graph adapter source-contract checks (20 assertions).

Java 21 compiler parser (`JavacTask.parse`) accepted 589 Java source files. This is syntax-only: it does not resolve imports, types or dependencies and is not a substitute for compilation.

## Maven limitation

Attempted:

`bash ./mvnw -B -ntp -Dphase0.postgres=true clean install`

The Maven wrapper attempted pinned Maven 3.9.16 downloads from `archive.apache.org` and `downloads.apache.org`; both failed with DNS resolution errors. The wrapper could not execute Maven. No Surefire, PostgreSQL integration or hosted CI result is claimed.

## Packaging checks

- Changed project files: {len(changed)} ({added} added, {replaced} replaced).
- Deleted project files: 0.
- Every project file in the archive is listed in `CHANGE_MANIFEST.csv` and `SHA256SUMS.txt`.
- Archive integrity and file hashes are checked during packaging.

## Research/evidence boundary

No 280-unit batch was executed. No outcome labels, trained artifacts, holdout results, transfer results, causal estimates or mechanism efficacy claims exist from this increment. The adapter currently records full-graph telemetry only, and batch metadata forbids confirmatory inference.
