# CoALA memory architecture — M2 implementation

## Purpose and boundary

M2 implements the four CoALA memory responsibilities as a framework-neutral Java module, `supply-chain-agent-memory`. The module owns memory models, lifecycle services, repository ports, deterministic retrieval and reference in-memory adapters. It does not depend on LangGraph4j, LangChain4j, JDBC, PostgreSQL or the supply-chain domain module.

The four memories are distinct responsibilities, not four labels over one generic vector store. Their different truth, lifecycle, temporal and retention rules are reflected in separate models and repository contracts.

## 1. Working memory — current execution context

`WorkingMemorySnapshot` is an immutable, versioned context scoped by `MemoryScopeId`. It contains typed slots, hypotheses, update/expiry timestamps and source references. Slots use the `MemoryValue` algebra (`Text`, `Decimal`, `Flag`, `Timestamp`, `EntityLink`, `TextList`) rather than arbitrary Java objects. Each `WorkingMemoryEntry` retains its own evidence references and update time.

Hypotheses are explicitly provisional (`PROPOSED`, `SUPPORTED`, `REFUTED`, `WITHDRAWN`). A supported hypothesis requires supporting evidence; a refuted hypothesis requires contradicting evidence. A hypothesis is not promoted to semantic knowledge merely because it is present in working memory.

Updates use optimistic version checks through `WorkingMemoryRepository.compareAndSet`. The scope and version are preserved, and expired working memory cannot be revived by a write. The snapshot's aggregate evidence list is derived from the evidence currently referenced by slots and hypotheses; replacing/removing a slot therefore removes evidence that no longer supports the active context.

Working memory is **not** LangGraph4j graph state or a graph checkpoint. Graph state carries orchestration data; working memory has its own context lifecycle, versioning and repository boundary.

## 2. Episodic memory — recorded experience

An `Episode` records a bounded experience: scope, title/summary, outcome, entities, ordered event records, event times, evidence and provenance. Model-derived episode summaries require evidence attached either to the episode or to one of its events.

New episodes begin `RECORDED`, not trusted. An explicit verification transition records reviewer, time and review evidence before the episode becomes `VERIFIED`. Rejection is recorded separately. Only episodes verified by the requested knowledge time are eligible for retrieval. A later supersession does not erase the fact that the episode was considered verified before its supersession time. Rejection and supersession are explicit audited lifecycle transitions; rejected episodes remain as review history and are not eligible for unverified-record purge.

Verified episodes are retained by default. Retention may purge only expired, unverified `RECORDED` episodes; rejected and verified/superseded history is not silently treated as disposable experience.

Episodes are not general application logs. They represent selected, structured experience that the agent may later retrieve as experience, with provenance and a review lifecycle.

## 3. Semantic memory — time-valid knowledge

`SemanticFact` represents a typed proposition about an entity (`subject`, `predicate`, typed value) with two distinct time axes:

- **Valid time** (`validFrom`, `validUntil`): when the proposition applies in the modeled world.
- **Knowledge time** (`recordedAt`, `activatedAt`, `supersededAt`): when the memory system recorded, admitted and later superseded the proposition.

The query therefore carries both `validAt` and `knownAt`. This permits questions such as “what supplier capacity applied on 1 June?” and “what did the agent know about that capacity on 15 May?” to produce different answers without rewriting history.

Facts are staged as `PROPOSED`. The conservative admission policy permits direct activation only for authoritative-system and human-verified provenance. Other sources, including model-derived content, verified documents, simulator outputs and episode-derived information, require explicit human approval. Overlapping, different-value active facts for the same scoped subject/predicate are rejected. Timeline amendments atomically replace the old fact with contiguous proposed segments that cover the old valid-time interval.

Semantic memory is not the source of operational truth. Authoritative supply-chain repositories and observations remain authoritative; semantic facts are sourced, time-aware representations that support reasoning and retrieval.

## 4. Procedural memory — versioned capabilities and procedures

A `ProcedureVersion` is an immutable reference to a versioned artifact, identified by location and SHA-256 digest. It can represent a validated runbook, capability definition, prompt/output schema, planning strategy or validation policy. The memory record stores metadata and the artifact identity, not mutable executable code or secrets.

The lifecycle is `DRAFT → VALIDATED → ACTIVE → RETIRED`, with audited rejection from draft. Rejection retains reviewer and time; semantic-fact rejection likewise records reviewer, time and evidence. Versions are append-only and carry predecessor references. Activating a new version validates both lifecycle transitions before retiring the old active version. A failed activation must not retire the currently active procedure. The in-memory reference adapter synchronizes reads and lifecycle writes so readers do not observe an intermediate “no active version” window.

The application must not autonomously rewrite or activate production procedures. Changes require reviewed artifacts, versioning and controlled deployment.

## Cross-memory retrieval and B1 boundary

`MemoryRetrievalService` is an exact deterministic composition of the four repositories. It enforces working-memory scope/expiry, retrieves verified episodes at a knowledge time, semantic facts at both valid and knowledge times, and active procedure versions at a knowledge time.

It does **not** implement B1 activation, associative retrieval, latency prediction, downstream utility estimation, ranking or maintenance-cost optimization. Those belong to the later Fan & Lan B1 mechanism and will consume these explicit memory contracts. Exact retrieval is intentionally kept separate from B1 selection policy.

## Maintenance and retention

`MemoryRetentionPolicy` supplies independent retention periods for working contexts, unverified episodes and superseded semantic facts. Maintenance may delete expired working snapshots, expired unverified episodes and sufficiently old superseded facts. It does not delete verified episodes or procedural version history automatically. Retention of source evidence and external artifact contents is governed by their owning systems.

## Persistence status

M2 supplies repository ports and thread-safe in-memory reference adapters for deterministic tests/local execution. These adapters are **not durable production storage**. The PostgreSQL DDL baseline is included at `supply-chain-agent-memory/src/main/resources/db/migration/V001__coala_memory_schema.sql` as a schema contract, but M2 does not execute it and does not include a JDBC adapter or migration runner.

M7 will select/integrate the migration runner and implement PostgreSQL adapters. Those adapters must preserve optimistic compare-and-set, scope isolation, bitemporal lookup, verified-only episode retrieval, atomic semantic timeline replacement and atomic procedure activation under concurrent application instances. Semantic-key conflict checks must be serialized at the database boundary (for example with a transaction-scoped advisory lock or SERIALIZABLE transaction/retry); in-memory `synchronized` methods alone are not a multi-instance guarantee.

A `MemoryScopeId` is an isolation key, not an authorization credential. Application authorization must happen before a service is allowed to access a memory identifier. Durable adapters must apply scope predicates at the data-access boundary where queries operate across scopes.
