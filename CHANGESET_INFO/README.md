# M8 application-completion changeset — GAP-07 / GAP-08 / GAP-10

## Apply

This is a **changes-only** archive. Extract its contents at the root of the existing `supply-chain-agent` repository so that each repository-relative path is preserved. Do not replace the repository with this archive; it is not a full-source snapshot.

The two obsolete source shadows listed in `DELETE_FILES.txt` must also be deleted from the repository. ZIP extraction does not delete existing files. Delete those exact paths through the GitHub repository UI after uploading the archive.

Then commit/publish the changeset and let the existing GitHub Actions workflow run. The new PostgreSQL integration test is enabled by the existing CI property `-Dphase0.postgres=true`; no local PC, Maven or terminal is required.

## Scope

- GAP-07: a versioned, typed CoALA learning-action surface for episodic, semantic and procedural memory, with scope-bound handlers, explicit capabilities and review/activation boundaries.
- GAP-08: a durable application composition root wiring the full cognitive-cycle graph, PostgreSQL persistence, checkpoint saver and one shared B6 execution guard; adds top-level cycle, typed resume and PostgreSQL restart/repeated-cycle integration coverage.
- GAP-10: deletes two obsolete unowned root-level Java shadows, extends the architecture guard and updates the reactor source/test inventory.

## Deliberate boundaries

- B3's global workspace snapshot is cycle-scoped and intentionally ephemeral; consumer receipts and outcome evidence are durable.
- Each host instance is bound to one `MemoryScopeId`.
- B1/B3 feedback policies remain neutral/shadow unless explicitly supplied by trusted configuration; B6 remains not calibrated without an approved calibration service.
- This remains a deterministic simulator-backed research/reference application, not a live ERP/WMS/TMS or supplier integration.
- This changeset adds the durable host's PostgreSQL startup, working-memory persistence, checkpoint resume after host restart and second-cycle ingress test. The combined full host-level plan approval → dispatch → reconciliation → restart journey is not yet exercised through this new host; existing full-graph/effect-boundary tests cover those runtime semantics through the evaluation composition.
- Do not claim empirical mechanism superiority, calibrated uncertainty or real-world operational performance from CI success.

See `docs/application/DURABLE-APPLICATION-HOST.md` for the contracts and known limitations.
