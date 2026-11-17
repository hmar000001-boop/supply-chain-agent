# M1 — Supply-chain domain model and deterministic simulator

## Purpose

M1 establishes the business world that later CoALA memory, action, Fan & Lan mechanism and planning components will operate on. It deliberately precedes cognitive orchestration. The domain and simulator must remain useful without LangGraph4j, LangChain4j, an LLM provider or PostgreSQL.

## Maven module boundaries

```text
supply-chain-agent-domain
    ^
    |
supply-chain-agent-simulator

supply-chain-agent-core       (framework-neutral application ports)
    ^
    |
supply-chain-agent-runtime  (LangGraph4j + LangChain4j adapters)
    ^
    |
supply-chain-agent-app      (composition root)
```

- `supply-chain-agent-domain` has no runtime or third-party dependency. It owns value objects, entity identities, business records, cross-reference validation and immutable world snapshots.
- `supply-chain-agent-simulator` depends only on the domain module. It applies typed commands to immutable snapshots and returns deterministic effects.
- The current core/runtime/app modules remain unchanged in responsibility. The later application layer may depend on the domain; the domain must never depend on the runtime or infrastructure.

## Initial domain areas

| Area | Principal concepts | Key invariant examples |
|---|---|---|
| Catalog | Product, SKU, product substitution | SKU uniqueness; substitutions are explicit, approved, time-bounded and unit-compatible. |
| Supplier | Supplier, product capability, capacity, lead time, quality qualification | A purchase order requires an active, qualified supplier and must fit the modeled capacity/lead-time horizon. |
| Inventory | Location, lot, quality status, reservation | No negative/over-reserved stock; quality-held or expired lots cannot be newly reserved. |
| Orders | Customer order, order line, demand, fulfilled quantity | Fulfilled plus actively reserved fulfillment cannot exceed demand. |
| Procurement | Purchase order, purchase-order line, receipt quantities and unit prices | Received quantity cannot exceed ordered quantity; supplier commitments consume capacity while open. |
| Logistics | Carrier, route, shipment, shipment lines | Carrier must support the shipment route; shipment lines must reference matching order lines and product units. |
| Disruption | Typed category, severity, lifecycle and affected references | Disruptions are modeled observations; recording one does not itself authorize or execute a business action. |
| Recovery | Recovery objective, protected constraints, proposed candidate actions | A candidate is a proposal, not an authorization or execution record. |

## Quantity and cost representation

- Quantities use `BigDecimal` and an explicit `UnitOfMeasure`; arithmetic rejects unit mismatches and underflow.
- Monetary values use `BigDecimal` and `Currency`; arithmetic rejects currency mismatches.
- Product substitution rules express the number of substitute units required per unit of the requested product. M1 requires both products to use the same base unit; broader cross-unit conversion is deferred until a concrete domain requirement justifies it.
- Supplier capacity is initially modeled for one explicit planning horizon per supplier/product capability. Time-phased capacity buckets are deferred; M5 planning can add them through an explicit model change rather than implying that a single scalar is a full capacity calendar.

## Snapshot invariants

`SupplyChainSnapshot` is immutable and versioned. Its builder validates references across products, suppliers, locations, inventory lots, orders, purchase orders, carriers, routes, shipments, reservations, recovery objectives and candidates. It validates:

1. SKU uniqueness (case-insensitive).
2. All referenced master records exist.
3. Product, lot, order, shipment, reservation and procurement quantity units agree.
4. Every substitute reservation cites a rule that was approved and effective when the reservation was created.
5. Active physical reservations do not exceed a lot's on-hand quantity.
6. Fulfilled plus actively reserved plus non-terminal shipment quantity does not exceed the customer order line's requested quantity.
7. Delivered shipment quantities are represented in the order line's fulfilled quantity and cannot exceed it.

A supplier capacity reduction may leave pre-existing confirmed purchase orders above the new capacity. That is a real disruption state, not an invalid snapshot. The simulator emits an explicit `OPEN_PURCHASE_ORDERS_AT_RISK` effect so the later decision procedure can investigate/recover it. New purchase orders cannot increase the over-commitment.

## Deterministic simulator contract

`SupplyChainSimulator.simulate(snapshot, command)` is a pure transition:

- It receives one immutable snapshot and one typed `SimulationCommand`.
- It performs deterministic domain validation only.
- It returns a new snapshot with the version advanced exactly once and typed `SimulationEffect` records.
- `commandId` is a correlation identifier in this M1 simulator, not an idempotency ledger; deduplication of retried external operations belongs to the M7 operation/inbox ledger.
- It performs no database write, network request, LLM call, supplier notification, inventory reservation in an external ERP, carrier booking or other external side effect.
- A failed command throws a typed `DomainViolation`; it does not mutate the input snapshot.

M1 transitions:

- Reduce supplier product capacity and flag existing open commitments that are now at risk.
- Place a draft purchase order after checking supplier status, quality qualification, lead time and capacity.
- Apply an inventory-lot quality hold and flag active reservations referencing that lot.
- Record a shipment delay and identify the affected customer orders.
- Reserve inventory for an order line using exact stock or an approved substitution.
- Release an active reservation.
- Change order demand without invalidating fulfilled or actively reserved quantities.
- Record a disruption observation.

## Explicitly deferred

- External connectors and real operational side effects (M3/M7).
- Authorization, approval and operation/idempotency ledgers (M3/M7).
- Temporal inventory/capacity projections, detailed replenishment optimization, carrier capacity and network routing constraints (extend when M5 planner scenarios require them).
- Evidence provenance and memory lifecycle (M2/M3).
- Recovery-plan generation, CoT/ToT/MCTS search and plan scoring (M5).
- Full orchestration of these transitions in LangGraph4j (M6).

The simulator must not be described as an ERP, an authorization service or a transaction manager. It is the deterministic world model used for domain validation, scenario evaluation and planning experiments.

## Validation

M1 adds domain and simulator tests for quantity/currency arithmetic, snapshot integrity, capacity reduction, purchase-order placement, exact and substituted inventory reservations, over-allocation rejection, quality holds, shipment delay, reservation release, order-demand changes, disruption recording and deterministic replay from the same input snapshot/command.
