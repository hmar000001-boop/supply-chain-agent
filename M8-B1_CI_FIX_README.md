# M8-B1 CI correction — change-only patch

Apply this small overlay at the repository root **after the original M8-B1 delivery**. It fixes the compile error reported by GitHub Actions in `GreedyDeterministicComparator.java:121` and adds a focused multi-depth regression assertion.

## Correction

`finalEvaluation` was declared inside the planner depth loop but referenced after the loop. The corrected code keeps `bestEvaluation` local to each depth, then updates `finalEvaluation` only when the corresponding `bestState` is committed. This keeps the evaluation attached to the exact selected path and preserves the last fully committed proposal when a later depth is interrupted.

The regression test asserts that REQ-01 retains two sequential reservations.

## Safety and scope

- Evaluation-only greedy comparator; not registered in the production planner router.
- No production planner behavior, action dispatch, M7 PostgreSQL code, schema or migrations changed.

## Validation

- Corrected comparator, its evaluation interface and result class compiled with Java 21 against isolated API stubs: passed (scope/syntax smoke only).
- Full Maven reactor / PostgreSQL integration tests: not run locally because Maven 3.9.16 wrapper bootstrap cannot reach its configured Apache download hosts in the authoring environment.
- Re-run GitHub Actions with `./mvnw -B -ntp -Dphase0.postgres=true clean verify` after applying this patch. CI acceptance remains pending until that run is green.
