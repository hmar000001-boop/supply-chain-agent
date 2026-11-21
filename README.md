# M8 Pre-B7 F03/F04 Changeset

This is an incremental, repository-root-relative changeset containing only the F03 and F04 implementation, tests, migrations, CI guardrails and traceability updates. It has no outer wrapper directory.

## Dependency / application order

This package is built on top of the exact green-CI source archive `supply-chain-agent-main (6).zip` **plus** the preceding F01/F02 changeset. Apply it only after F01/F02 has passed hosted CI and has been incorporated into the repository. Do not apply this package directly to the unmodified source archive.

Extract the ZIP at the repository root, allowing the complete source files to replace the existing files. No POM or dependency declarations are changed.

## F03 — governed B2 recovery knowledge

An approved B2 recovery draft can be staged as a CoALA procedural-memory `DRAFT`. Activation remains a separate procedural-memory governance step requiring an independent reviewer and approver. The artifact is immutable, content-addressed, scope checked and persisted through the new PostgreSQL V012 table. Later retrieval checks scope, source provenance, evidence freshness and current registered action descriptor fingerprints. It returns typed planning knowledge, not executable commands; it cannot dispatch actions. The normal M2 memory retrieval surfaces an active procedure as a procedural memory reference and B1 candidate.

The evaluation host uses in-memory stores. The PostgreSQL artifact adapter exists, but this change does not claim a durable production full-graph composition root.

## F04 — first-class B3 coalition competition

Workspace admission now evaluates bounded feasible coalitions (in addition to singleton candidates) as explicit alternatives. The selected coalition, evaluated alternatives and search-truncation status are preserved in the snapshot. If the bounded search truncates, the algorithm falls back to singleton admission rather than claiming optimality over an incomplete search. Scoring and enumeration are engineering choices, not a full LIDA reproduction or a formula prescribed by Fan & Lan.

## Validation

See `VALIDATION_REPORT.md` for exact checks and limitations. Hosted CI is authoritative for Maven compilation, full-graph tests and live PostgreSQL integration.
