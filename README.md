# M8 PRE-B7 R1-DEF — CI Fix 5

## Purpose

Fixes the final evaluation-test compilation error reported by GitHub Actions:

`M8B4FullGraphWorkflowTest.java:[546,44] unreported exception java.lang.Exception; must be caught or declared to be thrown`

`CompiledGraph.updateState(...)` is a checked-exception operation. The helper `ScenarioRuntime.resumeClarificationAfterRestart()` invokes it, while its signature previously did not declare the checked exception. The caller `R1DefFullGraphForkedProbe.main(...)` already declares `throws Exception`.

## Change

The complete source replacement changes only this helper signature:

```java
TerminalOutput resumeClarificationAfterRestart() throws Exception
```

No graph logic, persistence behavior, state mutation, or production source is changed.

## Apply

Extract this ZIP at the repository root and overwrite the matching source file. This package is incremental and must be applied on top of R1-DEF and CI Fixes 1–4.

Then run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

## Validation scope

- Verified the exact compiler-error call path and that the existing caller propagates `Exception`.
- Verified the complete replacement contains the expected signature exactly once.
- Verified ZIP integrity and SHA-256 manifest.
- Full Maven reactor/test compilation was not run locally; GitHub Actions remains the acceptance gate.
