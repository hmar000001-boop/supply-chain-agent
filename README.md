# M8 mechanism experiment pipeline — CI Fix 1

## Purpose

This changeset repairs the first hosted-CI compilation failure in the mechanism-specific experiment harness and fixes two related B4 evidence-integrity / Java 21 compilation defects discovered during the same audit.

## Apply

Extract this archive at the repository root, over the previously applied M8 experiment protocol, Scenario Factory v1, Full-Graph Adapter v1, and Mechanism-Specific Experiment Harness / Independent Evidence Pipeline v1 changesets.

All paths in this ZIP are repository-root-relative. Project delta: 10 complete file replacements, 1 new file, zero deletions.

## Main corrections

1. Adds a validating `RecoveryIntentionService.transitionKind(before, after)` classifier. It enforces same intention identity, exactly one version increment and an unchanged committed objective fingerprint; it classifies method changes, reconsideration requests, active-plan bindings and other lifecycle transitions.
2. Carries the authenticated actor explicitly through `RecoveryIntentionTransitionRecorder` and its in-memory entries. The evidence adapter now consumes this typed actor rather than trying to infer identity from free-text intention reasons.
3. Fixes a Java 21 effectively-final capture in `B4IntentionRunEvidence`.
4. Synchronizes the mechanisms test annotation inventory with the added regression test.
5. Refreshes the experiment candidate fingerprint and research registry metadata.

## Candidate identity

- Candidate: `M8-GAP01-GAP05-MECHANISM-EXPERIMENT-HARNESS-V1-CI-FIX1`
- Code-tree SHA-256: `8f64c3765be2e2eefe0ed1b00ff018526860e188ef6bb446a10dd1d60ef27236`
- Fingerprinted files: 674
- Git commit / hosted CI run ID / URL: unavailable in the supplied log; CI status remains unknown pending rerun.

## Validation

See `VALIDATION_REPORT.md`. Java 21 source compilation and focused B4 checks passed locally, as did the repository's static architecture/evidence/split guards. The full Maven reactor, Surefire engine and live PostgreSQL integration have not been run locally. Hosted CI must rerun the full command before this candidate is accepted.
