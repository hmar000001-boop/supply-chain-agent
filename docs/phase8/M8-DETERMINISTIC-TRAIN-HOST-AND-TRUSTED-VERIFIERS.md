# M8 deterministic TRAIN host and trusted verifier composition

**Status:** implementation delivered; hosted CI qualification and external trust provisioning remain required.  
**Protocol:** `M8-GAP01-05-IEP-1.1`  
**Primary execution profile:** `m8-scenario-fixture-deterministic-v1`

## 1. Concrete graph composition

`ScenarioFixtureDeterministicTrainPortsFactory` is the scenario-specific main-source implementation of `DeterministicTrainPortsFactory`. It is based on the already-tested full-graph scenario adapters, but constructs all mutable components independently for every `(TRAIN unit, mechanism, arm)` call:

- deterministic request interpretation; no `ChatModel`, live `ReasoningProvider`, provider credentials or network client is configured;
- real `FullGraphEvaluationHost` and LangGraph4j graph compilation through `ComposedDeterministicTrainSessionFactory`;
- fresh world/evaluator, decision/lifecycle/authority ports, action registry/handlers, workspace consumer, R2 repositories and audit counters;
- per-session `MemorySaver` checkpoint storage and a unique checkpoint thread ID;
- human approval interruption for all state-changing actions; the only continuation authority in this profile is DENIED;
- no external action dispatch, connector execution or production credentials.

The ports factory rejects non-TRAIN and foreign-protocol units. The composed session factory independently checks the split and protocol again. HOLDOUT and TRANSFER are rejected before a graph session is constructed.

The workspace consumer in this profile only acknowledges proposal consumption. It is not a downstream business outcome verifier. The configured R2 downstream-outcome and recovery-promotion verifiers reject the fixture-generated receipts/drafts. These restrictions are intentional: synthetic test receipts must not be converted into B1/B3 business outcomes.

## 2. What this host does and does not produce

The full-graph TRAIN host emits matched-arm graph telemetry and a run manifest. It does not issue verified outcome labels, promote recovery procedures, freeze a learning bundle, or authorize adaptive activation.

Independent simulator replay is a separate operation. Its evidence class remains `SIMULATOR_ONLY_NOT_BUSINESS_OUTCOME`. It may support simulator-grounded B6 labels after a separately configured verifier signs the exact label fingerprint. It must not be relabelled as real-world business evidence.

Consequently, this composition qualifies the deterministic graph execution boundary. A complete B1/B2/B3 training bundle still requires actual independently verified source evidence and valid external attestations; the host does not manufacture those records.

## 3. Trusted issuer provisioning

`M8TrustedResearchComposition.open(Configuration)` is the production trust composition root. It loads exactly seven distinct Ed25519 public-key files from a provisioned directory:

| Issuer role | Required issuer ID field | Attestation types |
|---|---|---|
| Endpoint evaluator | `endpointIssuerId` | `M8_ENDPOINT_HOLDOUT`, `M8_ENDPOINT_TRANSFER` |
| Safety auditor | `safetyIssuerId` | `M8_SAFETY_TRAIN`, `M8_SAFETY_HOLDOUT`, `M8_SAFETY_TRANSFER` |
| Hosted CI attestor | `ciIssuerId` | `M8_HOSTED_CI_SUCCESS` |
| B1 outcome verifier | `b1OutcomeIssuerId` | `M8_B1_DOWNSTREAM_OUTCOME` |
| B2 procedure verifier | `b2ProcedureIssuerId` | `M8_B2_APPROVED_PROCEDURE` |
| B3 coalition verifier | `b3OutcomeIssuerId` | `M8_B3_COALITION_OUTCOME` |
| B6 simulator verifier | `b6SimulatorIssuerId` | `M8_B6_SIMULATOR_LABEL` |

Each file must be named `<issuer-id>.pub` and contain one PEM `BEGIN PUBLIC KEY` / `END PUBLIC KEY` Ed25519 SubjectPublicKeyInfo block. The directory must contain exactly those seven regular files; symlinks, extra entries, private-key PEM and missing files are rejected.

**Private keys must remain outside this repository and outside the agent process**, preferably in an independently administered offline signing service/HSM. The application only loads pinned public keys and reads attestations. No key generation or signing endpoint is exposed by this composition.

## 4. Attestation file format

Attestations are read from the configured attestation directory using a strict read-only repository. Each file is named:

`<attestation-type>--<subject-sha256>.attestation`

Its UTF-8/ASCII content is exactly six data lines plus a final newline:

```text
M8-ATTESTATION-FILE-V1
<attestation-type>
<subject-sha256>
<issuer-id>
<issued-at-ISO-8601>
<standard-base64-Ed25519-signature>
```

The signed payload is the UTF-8 encoding of:

```text
M8-ATTESTATION-V1
<attestation-type>
<subject-sha256>
<issuer-id>
<issued-at-ISO-8601>
```

The subject fingerprint is the evidence-specific canonical fingerprint, not a filename chosen by the caller. For example, endpoint signers use `M8VerifiedEndpointEvidence.evidenceFingerprint()`, safety signers use `M8ExperimentSafetyEvidence.evidenceFingerprint()`, and the CI signer uses `GitCiM8FreezePrerequisiteVerifier.ciSubject(freezeContext)`.

Verification fails closed for missing records, unknown issuers, wrong issuer/type/subject, invalid signatures, future timestamps and attestations older than the configured maximum age. The file repository never writes or signs an attestation.

## 5. Composition usage

A host configures absolute paths for:

- the checked-out Git repository root;
- the externally provisioned public-key directory;
- the read-only attestation directory;
- a separate content-addressed TRAIN bundle archive directory.

It supplies the seven issuer IDs, stable freeze/bundle verifier IDs, a `Clock`, and an explicit maximum attestation age. The composition then exposes endpoint, safety, mechanism-bundle, B6-label and Git/CI prerequisite verifiers, a versioned bundle codec, an archive, and a `freezeService(auditor)` constructor.

The freeze service still requires the exact complete TRAIN telemetry audit, exact typed TRAIN bundle, zero safety counters, clean live Git tree, matching source/dependency fingerprints, successful hosted CI attestation and archive read-back. It does not open HOLDOUT or TRANSFER.

## 6. Qualification and evidence policy

Before any complete TRAIN evidence collection:

1. Apply the changeset and pass the full hosted 12-module Maven/PostgreSQL CI workflow.
2. Provision and independently review all seven issuer public keys.
3. Run the factory test and the actual full-graph approval-boundary composition test.
4. Verify that each arm has distinct repositories, consumers, action registry, world/evaluator, ports, graph runtime and checkpoint thread.
5. Run negative trust tests (missing key/attestation, wrong issuer, wrong type, altered subject/signature, expired/future attestation).
6. Keep the full 280-unit TRAIN telemetry run disabled until all previous gates are complete.

A green Java build establishes compilation and test correctness. It does not establish that the external issuer key custody is independent, that real business outcomes exist, or that the simulator predicts actual supply-chain operations. These require separate evidence and review.
