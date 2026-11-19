# M8 status — Evaluation and application freeze

**M8 objective:** evaluate the accepted supply-chain agent across multiple independent request families; compare decision procedures under matched conditions; measure the contribution and interactions of CoALA/Fan & Lan mechanisms; validate end-to-end safety and recovery; then freeze a reproducible application checkpoint before Chapters 19 and 20 are written.

## Baseline

- **Baseline ID:** `M8-M7-ACCEPTED-2026-11-19`
- **Accepted M7 repository checkpoint:** `SUPPLY_CHAIN_AGENT_M7_ACCEPTED_BASELINE_2026-11-19.zip`
- **Checkpoint SHA-256:** `7bf42b69f3f3db48e944fef907f9aa805bacf60bfa8f9b24b95a0a4636cdec49`
- **Baseline source-tree manifest SHA-256:** `08e82ac250aab5ec01271ca9ccea624d8aa7c0ad7a13bad2c680393f31667a8c`
- **Project files:** 556
- **M7 status:** accepted and closed, based on the user's confirmation that the complete GitHub Actions workflow is green on 2026-11-19. The accepted workflow runs the Java 21 Maven reactor, PostgreSQL integration tests, and the conditional backup/isolated-restore smoke step.

## M8 delivery status

| Delivery | Responsibility | Status |
|---|---|---|
| M8-B0 | Baseline reconstruction, architecture audit, test inventory, evaluation protocol and scenario register | **Complete** |
| M8-B1 | Expand scenario corpus and build reproducible evaluation/end-to-end harness; add a fair deterministic/greedy comparison adapter | Not started |
| M8-B2 | Compare CoT-style, ToT-inspired, deterministic/greedy, RAP-inspired MCTS and the hybrid router | Not started |
| M8-B3 | Run B1/B2/B3/B4/B6 ablations and selected interaction experiments | Not started |
| M8-B4 | End-to-end safety, failure, concurrency, restart and multi-request evaluation | Not started |
| M8-B5 | Analyze results, perform final quality/security/architecture review and correct proven defects | Not started |
| M8-B6 | Final application freeze and full repository checkpoint | Not started |
| M8-B7 | Approve Chapter 19/20 outlines, then write against the frozen code | Not started |

## M8-B0 acceptance

- Reconstructed the accepted M7 source from the exact uploaded repository archive and the three accepted M7-B2 overlays.
- Verified the reconstructed 556-file project tree byte-for-byte against the staged accepted repository tree.
- Created a full M7-accepted baseline ZIP and sorted per-file SHA-256 manifest.
- Audited all ten Maven modules, their production Java sources, test sources and annotated test methods/factories.
- Recorded the architecture-to-code traceability matrix, the pre-registered M8 evaluation protocol, explicit B3 outcome-attribution and B6 host-budget ownership gaps, and the M8 decisions log.
- Registered exactly ten request families and fourteen failure/concurrency/recovery scenarios from the Part III pilot requirements in `M8-SCENARIO-REGISTER.csv`; included a source-level test inventory in `M8-TEST-INVENTORY.csv`.
- No production Java, POM, migration, simulator or workflow code was changed in M8-B0. Only project documentation and phase status were updated.

## Phase boundaries

M8 evaluates the application and freezes it; it does not reopen M7 persistence infrastructure. WAL/PITR activation, tenant reconciliation for `__legacy_unscoped__`, deployment retention approval, external system credentials/adapters and enterprise identity integration remain deployment-owned rollout items. They are not silently marked complete by a green CI run.

Chapters 19 and 20 remain gated on the M8-B6 freeze and approval of their detailed outlines.
