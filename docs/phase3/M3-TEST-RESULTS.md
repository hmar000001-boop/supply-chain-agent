# M3 local test and validation record

Date: 2026-11-17

## Local Java 21 checks

| Scope | Test methods | Result | Execution mode |
|---|---:|---|---|
| `supply-chain-agent-domain` | 14 | 14 passed, 0 failed | Local JUnit-compatible reflection harness |
| `supply-chain-agent-memory` | 43 | 43 passed, 0 failed | Local JUnit-compatible reflection harness |
| `supply-chain-agent-simulator` | 20 | 20 passed, 0 failed | Local JUnit-compatible reflection harness |
| `supply-chain-agent-actions` | 25 | 25 passed, 0 failed | Local JUnit-compatible reflection harness |
| **Total local regression set** | **102** | **102 passed, 0 failed** | **Fresh instance per test method** |

The reflection runner creates a fresh test-class instance for each test method, matching JUnit Jupiter's default `PER_METHOD` lifecycle. This matters because the in-memory stores are stateful; reusing one test instance across methods would not reproduce the normal JUnit lifecycle.

The action suite includes 6 dispatcher tests, 7 internal-action tests, 11 perception/grounding tests and 1 catalog test. The simulator suite includes the three M3 rerouting cases plus the prior deterministic domain-transition cases.

Production sources in the domain, memory, simulator and action modules compiled using `javac --release 21 -Xlint:all`. Action and simulator test sources also compiled against a local JUnit API compatibility shim.

## Not claimed

- No local Maven/Surefire run is claimed.
- No local compile of the LangChain4j runtime adapter is claimed because Maven dependency artifacts are unavailable in the authoring environment.
- No real provider call is claimed.
- No durable action-ledger or real ERP/carrier side effect is claimed.
- No PostgreSQL memory migration or persistent action ledger was executed in M3.
- The 102-method local harness is a supplemental check; the user's clean GitHub Actions Maven reactor is authoritative for dependency resolution, real JUnit Platform behavior, module integration and preservation of M0/M1/M2 compatibility tests.
