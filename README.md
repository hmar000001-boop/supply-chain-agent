# M8 GAP-01–GAP-05 CI Fix 3

This repository-root-relative changeset corrects one stale assertion in the B4 full-graph regression test.

## Apply

Extract this ZIP at the repository root, over the GAP-01–GAP-05 candidate with CI Fix 1 and CI Fix 2 already applied. It replaces one complete test source file and adds one research note. No production Java source is changed.

## Verify

Run the full repository command:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

The hosted CI result remains authoritative. This delivery does not claim a green build.
