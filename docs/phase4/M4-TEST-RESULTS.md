# M4 local test and validation record

Date: 2026-11-17 (repeated fresh local run)

## Local Java 21 checks

| Scope | Test methods | Result | Execution mode |
|---|---:|---|---|
| M1 domain | 14 | 14 passed, 0 failed | Local JUnit-compatible reflection harness |
| M2 CoALA memory | 43 | 43 passed, 0 failed | Local JUnit-compatible reflection harness |
| M1 deterministic simulator | 20 | 20 passed, 0 failed | Local JUnit-compatible reflection harness |
| M3 CoALA actions | 25 | 25 passed, 0 failed | Local JUnit-compatible reflection harness |
| M4 mechanisms | 70 | 70 passed, 0 failed | Local JUnit-compatible reflection harness |
| **Total local non-runtime regression set** | **172** | **172 passed, 0 failed** | **Fresh instance per test method** |

M4 test split:

| Mechanism | Test methods |
|---|---:|
| B1 activation/retrieval and M2 candidate adapter | 14 |
| B2 impasse detection and child lifecycle | 11 |
| B3 workspace admission, coalitions and broadcast | 16 |
| B4 intention/method lifecycle | 13 |
| B6 uncertainty, budgets and meta-control | 16 |
| **Total** | **70** |

Production and test sources in the domain, memory, simulator, actions and mechanisms modules were compiled with Java 21. M4 production compilation used `javac --release 21 -Xlint:all`. The harness creates a fresh test instance for every `@Test` method, matching JUnit Jupiter's default per-method lifecycle; this avoids in-memory repository state leaking between test methods.

## Additional cases covered

- B1: all four M2 memory types bridged; scope and lifecycle/knowledge-time validation; activation/latency distinction; downstream utility and maintenance cost; bounded context selection; idempotent actual maintenance accounting.
- B2: capability subset and allow-list, scope, action budget, duplicate signal consolidation, parent version/objective check, evidence-backed findings, unresolved escalation and expiry.
- B3: mandatory protection, capacity/count failure without partial snapshot, symmetric conflicts, complementary coverage, coalition accounting, incomplete coverage, scoped closure/broadcast, malformed consumer receipt and consumer failure.
- B4: protected objective fingerprint, method change only after reconsideration, suspension/resume, bounded delegation return, evidence-backed completion, future-dated proof rejection, stale version, delimiter-safe fingerprint and monotonic termination time.
- B6: concurrent non-oversubscribing reservations, idempotent reservation/commit/release, overrun recording, deadline, uncertainty routing, budget replenishment interruption, unused planner budget isolation and overflow-safe accounting.

## Not claimed

- No local Maven/Surefire or full reactor run is claimed.
- No local LangGraph4j runtime compatibility tests are claimed in this harness; those remain in the Maven reactor and passed M0 CI previously.
- No PostgreSQL M4 repository/ledger migration was executed; M4 adapters are in-memory and M7 supplies durable implementations.
- The 172-method local harness is supplemental. The user's GitHub Actions Maven/Surefire result is authoritative for M4 acceptance and preservation of the complete reactor.
