# Validation report — F-01 / F-02

## Source baseline

- Uploaded base archive: `supply-chain-agent-main (6).zip`
- SHA-256: `ea765285e4a20520bd51cf49abaa02dedc8382015fce325862b4ab86dc1894a6`
- Target: Java 21; 11 Maven modules.

## Checks performed locally

| Check | Result | Notes |
|---|---|---|
| Java syntax parse | PASS | 523 Java files parsed; zero syntax errors. |
| Framework-neutral production compile | PASS | 349 Java source files across domain, memory, actions, mechanisms, planning, core and simulator; `javac --release 21 -Xlint:all`. |
| Changed runtime service compile | PASS | `CognitiveCycleOperations`, `ResourceBudgetExecutionGuard`, `CognitiveCycleDecisionServices`, `CognitiveCycleLifecycleServices`; compiled against compiled project modules and minimal B2/LangGraph interfaces where required. |
| Changed runtime graph/state compile | PASS WITH STUBS | `CognitiveCycleGraph` and `CognitiveCycleState` compiled against narrow LangGraph4j/B2 API stubs. This does not prove compatibility against the real LangGraph4j artifact. |
| Selected test source compile | PASS WITH JUNIT SHIM | Five selected test classes compiled against local JUnit-compatible stubs. |
| Selected test execution | PASS WITH JUNIT SHIM | 66 selected test methods passed under a lightweight local reflection runner; not Maven Surefire or the real JUnit runtime. |
| Full evaluation test syntax | PASS | All 523 Java files, including `M8B4FullGraphWorkflowTest`, parsed successfully. |
| Full evaluation graph test compile/execution | NOT RUN | Real LangGraph4j/JUnit/PostgreSQL dependency jars were unavailable locally. |
| Full Maven reactor | NOT RUN | Maven executable/dependency cache and wrapper distribution were unavailable in the environment. |
| PostgreSQL migrations/integration tests | NOT RUN | Must be confirmed in hosted CI. |

## Selected tests executed (66)

- `PlannerRouterTest`: 8
- `PlannerBudgetTest`: 3
- `MetaControlGovernorTest`: 18
- `CognitiveCycleDecisionServicesTest`: 13
- `CognitiveCycleLifecycleServicesTest`: 24

## Important limitations

- The full-graph B4/B6 tests are included in source, but their actual execution depends on hosted CI with the project's pinned dependencies.
- The local LangGraph4j stubs validate Java type use against the shapes represented by those stubs; they are not a substitute for compilation against the real library.
- No empirical improvement claim is made for B4/B6. The change demonstrates control propagation and bounded behavior in deterministic tests.
- F-03 and F-04 are intentionally outside this changeset.

## Required hosted validation

Run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

Only report the change as CI-green after this command and the related GitHub Actions jobs pass on the commit containing this changeset.
