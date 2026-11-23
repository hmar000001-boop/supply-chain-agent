# M8 Trusted Research CI Fix 2 — Effective-input provenance contract

## CI evidence reviewed

The hosted run at `2026-11-22T22:56:01Z` completed modules 1–11 successfully and compiled all 106 production source files in `supply-chain-agent-evaluation`. It then failed during test compilation of the 20 evaluation test sources with exactly one reported compiler diagnostic:

- `M8B4FullGraphWorkflowTest.java:252`: `FullGraphExperimentRunObservation.effectiveInputFingerprint()` did not exist.

No evaluation-module tests ran in that build because `testCompile` failed first. The log does not show a Maven dependency, PostgreSQL, test assertion, or production-source compile failure.

## Root cause

The M8 scenario-unit contract and matched-pair contract already carried `effectiveInputFingerprint`, and `FullGraphExperimentAdapter` already included that fingerprint in the canonical observation-fingerprint calculation. However, the per-arm `FullGraphExperimentRunObservation` record omitted the value as a component and from its JSONL representation. The deterministic TRAIN host qualification test added in the host-composition changeset correctly expects the per-arm observation to expose this provenance identifier. The contract was only partially propagated across the adapter boundary.

This was also an integrity gap, not just a missing accessor: consumers of individual arm JSONL records could not directly bind an observation to the exact effective generated inputs, even though the pair-level record had the value. The pair constructor did not independently assert that each arm's observation referred to that pair-level fingerprint.

## Repair

1. Add `effectiveInputFingerprint` as a required component of `FullGraphExperimentRunObservation`.
2. Serialize it in every `fullGraphRun` JSONL record.
3. Populate it directly from `session.unit().effectiveInputFingerprint()` in the full-graph adapter; do not derive it from a request fingerprint or fixture fingerprint.
4. Strengthen `FullGraphExperimentPair` so both baseline and treatment observations must match the pair's effective-input fingerprint.
5. Update the sole direct test construction of the observation record and add regression coverage for JSONL serialization and rejection of a mismatched matched arm.

The existing observation fingerprint canonicalization already included the effective-input fingerprint; the new field exposes the exact same value rather than changing that input or introducing a second fingerprint algorithm.

## Candidate fingerprint

The canonical code-tree fingerprint was regenerated after the API and regression-test changes:

- Candidate ID: `M8-GAP01-GAP05-EFFECTIVE-INPUT-PROVENANCE-CI-FIX2`
- SHA-256: `62d1b39c75aab9a87b136a75d97c31b2371c0a396fc941581a5bbc614aa83b64`
- Included executable/configuration files: `719`
- Hosted CI conclusion: pending; this metadata does not attest a green build.

## Build interpretation

The log confirms that all 11 upstream modules passed their compile/test/package/install phases, including PostgreSQL integration tests. Evaluation production compilation passed. After this test-source API correction, a new hosted run is still required to execute the evaluation tests and complete the reactor. Any later failures would be newly observable test/runtime failures; they cannot be inferred from the supplied log.

## Scope and non-claims

No production action behavior, experiment allocation, training data, outcome labels, or TRAIN/HOLDOUT/TRANSFER gate is changed. No TRAIN execution or outcome collection is authorized by this repair. The candidate fingerprint metadata is refreshed for the changed code tree, but no hosted-CI attestation is claimed.
