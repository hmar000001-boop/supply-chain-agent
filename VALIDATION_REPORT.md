# Validation report — R2-02 CI Fix 3

## Root cause confirmed from source and the supplied CI log

The reported failure was in `M8B4FullGraphWorkflowTest.fullGraphResolvedB2ReturnCreatesTypedRecoveryDraftAndCarriesReference`. The failing assertion checked that the parent graph state contained the recovery-procedure draft ID after a resolved B2 return.

The evaluation service can return `NO_FEASIBLE_CANDIDATE`; the graph's evaluation route previously sent only `IMPASSE` to B2 and sent `NO_FEASIBLE_CANDIDATE` to its default escalation edge. The child therefore never created or returned the draft for this scenario.

A second integration defect was identified in the child-context adapter: it reconstructed `CycleContext` with blank working-memory and intention identities (and omitted the active planning/evidence references). B2 lifecycle operations need these identities to load the active parent records, validate the delegated intention, and verify the return against the current working-memory/objective version.

## Changes made

- Routed `NO_FEASIBLE_CANDIDATE` through the pre-impasse uncertainty refresh and B2 resolution path.
- Preserved active working-memory and intention IDs/versions and the active evidence, workspace, planner/session, candidate-set, selected-candidate and uncertainty references across the child boundary.
- Preserved the original impasse reason for opening the B2 case.
- Kept parent request/summary/clarification content, memory contents, candidate objects and capability grants out of child graph state. The authoritative content and permissions remain in the parent-owned stores and B2 case.
- Used the existing parent `IMPASSE_CASE_ID` to prevent a second B2 attempt under the same execution-scoped terminal case identity. A resolved child return causes a parent context reload/re-evaluation; any subsequent impasse escalates. This loop bound is an engineering policy, not a paper-specific claim.
- Left the existing integration and fail-closed tests unchanged.

## Local checks actually performed

- JDK 21 `javac --release 21` compiled `B2ResolutionGraphState` and `B2ResolutionSubgraph` against small API-compatible LangGraph4j / application-contract stubs: **passed**.
- A reflection-based mapping check invoked the child-context adapter and verified preservation of working-memory ID/version, intention ID/version, candidate-set ID, impasse reason, and uncertainty version; it also verified that the parent's request and summary text are not copied into child context: **passed**.
- JDK 21 parser check (`JavacTask.parse`, `--release 21`, `-proc:none`) over all three changed Java source files: **passed**.
- Source-level inspection verified the graph route targets the existing `b2-resolution` edge and that the parent result node copies the child's disposition, case ID, investigation reference, uncertainty revision, and recovery draft ID into parent state: **passed**.
- ZIP structure and SHA-256 verification: **passed**.

## Not performed / remaining authority

The complete Maven reactor was **not** run locally. The Maven Wrapper could not download Maven in this environment because DNS/network access to the Maven distribution host was unavailable; no local `mvn` executable was installed. Stub compilation and parser checks do not replace compilation against the project's actual dependency graph or execution of its full tests.

Therefore this package is **not declared CI-green**. Run the full command above in the repository. The most direct acceptance check is the existing test `M8B4FullGraphWorkflowTest.fullGraphResolvedB2ReturnCreatesTypedRecoveryDraftAndCarriesReference`; the existing `readOnlyAndInfeasibleFixturesFailClosedWithoutAWrite` test should also continue to pass, along with all other modules and PostgreSQL tests.
