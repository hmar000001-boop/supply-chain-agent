# M8 Effective Input / B3 SHADOW overlay — CI Fix 3 (changes only)

Apply this archive by extracting it at the repository root and preserving its directory structure.

This is an incremental changeset for the source state after **M8 Effective Input Provenance CI Fix 2**. It is not a complete repository and must not be extracted over a different project revision.

## Summary

- Modified: 4 existing project files
- Added: 1 research/validation note
- Deleted: 0 files
- Project files in this archive: 5
- No Maven POM, Maven wrapper, generated output, or unchanged repository content is included.

## Root cause

The deterministic TRAIN factory configured the same neutral B3 coalition SHADOW adjustment twice: once in `R2WorkflowConfiguration.experimentShadowCoalitionFeedbackAdjustment` and again in the per-arm `DeterministicTrainSessionConfiguration.b3ShadowAdjustment`. The full graph host correctly rejects a second overlay. The fix removes the redundant R2-level overlay and keeps the explicit per-arm overlay as the single source. The host's fail-closed duplicate check remains unchanged.

## Files modified

1. `supply-chain-agent-evaluation/src/main/java/dev/supplychain/agent/evaluation/experiment/host/ScenarioFixtureDeterministicTrainPortsFactory.java` — pass `null` for the optional R2-level experiment overlay.
2. `supply-chain-agent-evaluation/src/test/java/dev/supplychain/agent/evaluation/experiment/host/ScenarioFixtureDeterministicTrainPortsFactoryTest.java` — assert that neither arm carries a second R2-level overlay and that the per-arm overlay remains configured.
3. `docs/research/M8-GAP01-GAP05-MECHANISM-EXPERIMENT-HARNESS-CANDIDATE-FINGERPRINT.json` — regenerate candidate fingerprint for Fix 3.
4. `docs/research/CONFORMANCE_ASSESSMENT_METADATA.json` — align candidate ID, fingerprint and hosted-CI pending status.

## File added

5. `docs/research/M8-TRUSTED-RESEARCH-CI-FIX3-B3-SHADOW-OVERLAY-2026-11-23.md` — diagnosis, repair rationale and validation record.

No files are deleted. Test inventory counts do not change because the existing test method is extended rather than adding a new test method.

## Apply and validate

Extract the ZIP at repository root, then run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

Do not begin TRAIN evidence collection or open HOLDOUT/TRANSFER until the full reactor and the existing independent-evidence gates pass.
