# M1 status — domain foundation and deterministic world simulator

**Status: accepted — GitHub Actions CI passed on 2026-11-17.**

## Added capabilities

- New framework-independent `supply-chain-agent-domain` Maven module.
- New `supply-chain-agent-simulator` Maven module, depending only on the domain module.
- Typed product, supplier, inventory, order, procurement, logistics, disruption and recovery models.
- Strongly typed entity identifiers, unit-aware `Quantity`, and currency-aware `Money` value objects.
- Immutable, versioned `SupplyChainSnapshot` with cross-aggregate reference, substitution, reservation, shipment and order-fulfillment invariants.
- Pure deterministic simulator commands for supplier capacity reduction, purchase-order placement, quality hold, shipment delay, inventory reservation/release, order-demand change and disruption recording.
- Explicit simulation effects for open purchase orders exposed by a capacity reduction, active reservations affected by quality holds, shipment delays and other modeled outcomes.
- Domain and simulator tests, including purchase-order receipt-state invariants.
- Existing Phase 0 modules and all seven framework/checkpoint compatibility tests remain in the Maven reactor and are not modified by this M1 changeset.

## Authoring-environment validation

The following checks were performed after the final M1 source edits:

- All domain production sources compiled with JDK 21 using `javac --release 21 -Xlint:all`.
- All simulator production sources compiled against the compiled domain module with the same Java 21 settings.
- All M1 test sources compiled against a local JUnit API compatibility shim because the authoring environment has no usable Maven dependency cache/network.
- A local reflection runner executed all **31 M1 test methods**; all passed. This is not a claim that the JUnit engine or Maven Surefire ran locally.
- The deterministic scenario smoke harness passed supplier-capacity exposure, immutable versioning, exact reservation, substitution ratios, quality holds, shipment delay, disruption recording, purchase-order checks and over-allocation rejection.
- All Maven POM files parsed as XML; all five reactor module paths exist.
- Domain and simulator production sources contain no LangGraph4j, LangChain4j, JDBC or PostgreSQL imports.

The complete Maven reactor, Maven Enforcer and real JUnit/Surefire execution were verified by the user-reported green GitHub Actions run on 2026-11-17. The authoring-environment checks above remain supplementary rather than a replacement for that CI evidence.

## Scope boundaries

This is the domain/simulator layer, not a complete agent implementation. It makes no LLM calls and has no LangGraph4j dependency. The simulator has no external side effects and does not implement authorization, external action execution or an idempotency ledger. `commandId` is a deterministic simulation correlation ID, not a durable deduplication key. Supplier capacity is represented for one planning horizon per supplier/product capability in this first increment; time-phased capacity, richer logistics optimization and external adapters remain later extensions.

## Next

M1 passed the clean CI build. M2 now implements CoALA working, episodic, semantic and procedural memory on framework-neutral scope/entity/evidence references; M1 domain/simulator invariants remain mandatory tests.
