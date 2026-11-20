#!/usr/bin/env python3
"""Static guardrails for the grouped M8-PreB7 R2 delivery; standard library only."""
from __future__ import annotations

import csv
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        FAILURES.append(f"missing required file: {relative}")
        return ""
    return path.read_text(encoding="utf-8", errors="replace")


# B2 draft boundary: verified, typed, registered descriptors; no executable dispatcher surface.
b2 = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/b2/RecoveryProcedureDraftService.java")
require("impasse.status()!=ImpasseCaseStatus.RESOLVED" in b2, "B2 draft creation must require a resolved child case")
require("objectiveFingerprint()" in b2 and "workingMemoryVersion()" in b2, "B2 must bind the current parent objective and working-memory version")
require("actions.descriptor(id)" in b2, "B2 draft action references must resolve through the registered ActionRegistry")
require("evidenceVerifier.verify" in b2, "B2 draft and approval must revalidate scoped evidence")
require("approvalAuthority.isAuthorized(current" in b2, "B2 review transition must require host approval authority")
require("public RecoveryProcedureDraft execute" not in b2 and "public void dispatch" not in b2,
        "B2 draft service must not expose an execution/dispatch entry point")
ref = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/b2/RegisteredRecoveryActionReference.java")
require("descriptorFingerprint" in ref and "Capability" in ref and "ActionEffect" in ref,
        "B2 stored actions must be registered typed capability/effect references with descriptor fingerprints")

# B3 persistence must be evidence-only and read-time revalidated before training capability minting.
b3 = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/b3/B3OutcomeEvidenceService.java")
require("repository.appendReceipt(evidence)" in b3 and "repository.appendOutcome(outcome)" in b3,
        "B3 must durably append consumer receipts and verified downstream outcomes")
require("repository.findReceipt(scopeId,outcome.receiptId())" in b3 and "verifier.verify(scopeId,outcome.receiptId()" in b3,
        "B3 must re-link and reverify persisted outcomes before training input is minted")
require("new VerifiedDownstreamOutcomeEvidence(outcome)" in b3,
        "B3 must mint the verified-evidence capability only after revalidation")
wrapper = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/b3/VerifiedDownstreamOutcomeEvidence.java")
require("VerifiedDownstreamOutcomeEvidence(DownstreamOutcomeEvidence evidence)" in wrapper
        and "public VerifiedDownstreamOutcomeEvidence(" not in wrapper,
        "Verified B3 evidence constructor must remain package-private")
receipt = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/b3/WorkspaceReceiptEvidence.java")
require("scopeId" in receipt and "consumedCandidateFingerprints" in receipt and "admissionPolicyVersion" in receipt,
        "B3 receipt must bind scope, consumed candidate fingerprints and admission-policy lineage")

# Adaptation: only verified outcome capability is trainable; scope-bound; shadow by default.
trainer = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/feedback/OutcomeFeedbackPolicyTrainer.java")
require("List<VerifiedDownstreamOutcomeEvidence> verifiedOutcomes" in trainer,
        "trainer must accept only the B3 verified-evidence capability")
require("PolicyDisposition.SHADOW" in trainer and "maximumAbsoluteAdjustment>5.0" in trainer,
        "trainer must be bounded and produce shadow-only candidates")
active = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/feedback/ActiveOutcomeFeedbackPolicy.java")
require("scopeId.equals(requestedScope)?" in active,
        "active policy must return zero adjustments outside its bound scope")
selector = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/b1/B1RetrievalSelector.java")
admission = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/b3/WorkspaceAdmissionService.java")
require("CandidateOutcomeAdjustment.neutral()" in selector and "CandidateOutcomeAdjustment.neutral()" in admission,
        "B1/B3 constructors must preserve neutral feedback by default")
require("adjustmentFor(candidate.scopeId()" in selector and "adjustmentFor(candidate.scopeId()" in admission,
        "B1/B3 must request feedback using the candidate scope")
evaluator = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/feedback/FeedbackPolicyHoldoutEvaluator.java")
require("Collections.disjoint(candidate.trainingCycleIds(),ids)" in evaluator,
        "holdout evaluator must reject overlap with training cycles")
require("minimumHoldoutScenarios()" in evaluator and "minimumMeanUtilityGain()" in evaluator
        and "maximumSafetyRegressions()" in evaluator and "maximumMeanResourceIncrease()" in evaluator,
        "holdout evaluator must enforce all preregistered threshold dimensions")
require("candidate.sourceFingerprint()" in evaluator and "candidate.candidateAdjustments()" in evaluator
        and "sha256(canonical)" in evaluator,
        "evaluation fingerprint must bind the exact candidate payload and source")
activation = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/feedback/FeedbackPolicyActivationService.java")
require("approval.evaluationFingerprint()" in activation and "authority.isAuthorized(candidate,report" in activation,
        "activation must bind the exact report and delegate to host authority")
require("report.decision()!=PolicyDisposition.ELIGIBLE_FOR_AUTHORIZED_ACTIVATION" in activation,
        "activation must reject any candidate that fails held-out evaluation")

# PostgreSQL migration and typed repositories.
migration = ROOT / "supply-chain-agent-infrastructure-postgres/src/main/resources/db/migration/V011__b2_procedure_drafts_and_b3_outcome_evidence.sql"
require(migration.is_file(), "V011 R2 PostgreSQL migration is missing")
if migration.is_file():
    sql = migration.read_text(encoding="utf-8")
    for table in ("b2_recovery_procedure_draft", "b2_recovery_procedure_draft_history",
                  "b3_workspace_consumer_receipt", "b3_downstream_outcome"):
        require(f"CREATE TABLE {table}" in sql, f"V011 is missing {table}")
    require("FOREIGN KEY (scope_id, receipt_id)" in sql, "B3 outcome table must be bound to a persisted receipt")
    require("CREATE TABLE workspace_snapshot" not in sql and "CREATE TABLE b3_workspace_snapshot" not in sql,
            "R2 must not persist live B3 workspace snapshots")
runner = read("supply-chain-agent-infrastructure-postgres/src/main/java/dev/supplychain/agent/infrastructure/postgres/migration/PostgresMigrationRunner.java")
require('Migration(11, "b2_procedure_drafts_and_b3_outcome_evidence"' in runner,
        "PostgreSQL migration runner must register V011 as version 11")
require((ROOT / "supply-chain-agent-infrastructure-postgres/src/main/java/dev/supplychain/agent/infrastructure/postgres/runtime/PostgresRecoveryProcedureDraftRepository.java").is_file(),
        "typed PostgreSQL B2 repository is missing")
require((ROOT / "supply-chain-agent-infrastructure-postgres/src/main/java/dev/supplychain/agent/infrastructure/postgres/runtime/PostgresB3OutcomeEvidenceRepository.java").is_file(),
        "PostgreSQL B3 evidence repository is missing")
require((ROOT / "supply-chain-agent-infrastructure-postgres/src/test/java/dev/supplychain/agent/infrastructure/postgres/runtime/R2OutcomeEvidencePostgresIntegrationTest.java").is_file(),
        "R2 PostgreSQL repository replacement integration test is missing")

# Deterministic no-corpus artifact: do not invent results or claim policy success.
cli = read("supply-chain-agent-evaluation/src/main/java/dev/supplychain/agent/evaluation/app/M8R2OutcomeFeedbackEvidenceCli.java")
for token in ('NO_VERIFIED_OUTCOME_CORPUS', '"activationStatus": "DISABLED"',
              '"empiricalAdvantageEstablished": false', '"baselinePreserved": true',
              'PolicyDisposition.SHADOW', 'm8-r2-outcome-feedback-evidence.json', 'SHA256SUMS.txt'):
    require(token in cli, f"R2 evidence CLI is missing required truthful status token: {token}")
require((ROOT / "supply-chain-agent-evaluation/src/test/java/dev/supplychain/agent/evaluation/M8R2OutcomeFeedbackEvidenceCliTest.java").is_file(),
        "R2 evidence CLI deterministic test is missing")
require((ROOT / "supply-chain-agent-mechanisms/src/test/java/dev/supplychain/agent/mechanisms/b2/RecoveryProcedureDraftServiceTest.java").is_file(),
        "B2 draft adversarial test is missing")
require((ROOT / "supply-chain-agent-mechanisms/src/test/java/dev/supplychain/agent/mechanisms/b3/B3OutcomeEvidenceServiceTest.java").is_file(),
        "B3 receipt/outcome test is missing")
require((ROOT / "supply-chain-agent-mechanisms/src/test/java/dev/supplychain/agent/mechanisms/feedback/OutcomeFeedbackPolicyTest.java").is_file(),
        "feedback holdout/activation/scope test is missing")

# CI mirror and artifact/security coverage.
active_workflow = ROOT / ".github/workflows/ci.yml"
mirror_workflow = ROOT / "workflows/ci.yml"
require(active_workflow.is_file() and mirror_workflow.is_file(), "active and mirror workflow files are required")
if active_workflow.is_file() and mirror_workflow.is_file():
    require(active_workflow.read_bytes() == mirror_workflow.read_bytes(), "CI workflow mirror is not byte-identical")
    workflow = active_workflow.read_text(encoding="utf-8")
    for token in ("verify-m8-r2-evidence.py", "M8AblationCli", "M8R2OutcomeFeedbackEvidenceCli",
                  "target/m8-b3-results/**", "target/m8-r2-results/**", "target/security/**",
                  "github/codeql-action/init@v4", "github/codeql-action/analyze@v4",
                  "aquasecurity/trivy-action@v0.36.0", "trivy-fs.sarif", "TOOL-VERSIONS.txt"):
        require(token in workflow, f"CI workflow missing R2 evidence/security step or artifact: {token}")
    require("./mvnw -B -ntp -Dphase0.postgres=true clean install" in workflow,
            "CI must retain the complete Java 21/PostgreSQL reactor acceptance command")

# Acceptance register must show R1 closed and one combined R2 row.
register_path = ROOT / "docs/pre-b7/M8-PREB7-ACCEPTANCE-REGISTER.csv"
if register_path.is_file():
    with register_path.open(newline="", encoding="utf-8") as f:
        rows = {row["ID"]: row for row in csv.DictReader(f)}
    require(rows.get("R1-04", {}).get("Status") == "ACCEPTED", "R1-DEF must be registered accepted after user-confirmed green CI")
    require(rows.get("R2-01", {}).get("Status") == "IMPLEMENTED_PENDING_CI", "R2 must remain pending until its single hosted acceptance run")

if FAILURES:
    print("M8-PreB7 R2 evidence verification: FAILED", file=sys.stderr)
    for failure in FAILURES:
        print(f" - {failure}", file=sys.stderr)
    raise SystemExit(1)

print("M8-PreB7 R2 evidence verification: PASS")
print("B2 typed draft/non-execution boundary: PASS")
print("B3 receipt-linked verified outcomes/ephemeral workspace boundary: PASS")
print("B1/B3 shadow, scope and holdout activation guardrails: PASS")
print("V011 repositories and PostgreSQL integration test presence: PASS")
print("No-corpus R2 artifact and hosted evidence/security workflow: PASS")
