#!/usr/bin/env python3
"""Static source guard for the pre-B7 F-03/F-04 mechanism increments."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        FAILURES.append(f"missing required source: {relative}")
        return ""
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


# F-03: the already-approved B2 draft is only staged into CoALA procedural memory.
promotion = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/b2/RecoveryProcedurePromotionService.java")
for token in (
    "stageApprovedDraft", "RecoveryProcedureStatus.APPROVED", "proceduralMemory.stage(staged)",
    "public ProcedureVersion validate", "public ProcedureVersion activate", "public ProcedureVersion reject",
    "retrieveActive", "verifyEvidenceAndDescriptors", "requireIndependentActor",
):
    require(token in promotion, f"F-03 governed B2 promotion is missing required boundary: {token}")
require("proceduralMemory.activate" not in promotion.split("public ProcedureVersion stageApprovedDraft", 1)[1].split("public ProcedureVersion validate", 1)[0],
        "B2 approval must not automatically activate CoALA procedural memory")

artifact = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/b2/RecoveryProcedureArtifact.java")
for token in ("RegisteredRecoveryActionReference", "preconditionKeys", "expectedPostconditionKeys",
              "failureConditionKeys", "EvidenceReference", "sourceFingerprint"):
    require(token in artifact, f"F-03 immutable typed artifact lacks {token}")
require("String instruction" not in artifact and "Map<String, Object> arguments" not in artifact,
        "F-03 artifact must not introduce free-form executable instructions or action arguments")

retrieval = read("supply-chain-agent-memory/src/main/java/dev/supplychain/agent/memory/retrieval/MemoryRetrievalService.java")
candidate_factory = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/b1/B1MemoryCandidateFactory.java")
require("procedural.findActiveAt(query.scopeId(), query.knownAt(), query.limitPerType())" in retrieval,
        "standard CoALA retrieval must return active procedural memories")
require("retrieved.activeProcedures().forEach" in candidate_factory,
        "active CoALA procedures must enter the normal B1 candidate set")

host = read("supply-chain-agent-evaluation/src/main/java/dev/supplychain/agent/evaluation/host/FullGraphEvaluationHost.java")
runtime = read("supply-chain-agent-evaluation/src/main/java/dev/supplychain/agent/evaluation/host/FullGraphRuntime.java")
require("new RecoveryProcedurePromotionService(recoveryDraftService, proceduralMemory, recoveryArtifactStore" in host,
        "full evaluation graph must compose the governed B2-to-CoALA promotion service")
require("new MemoryRetrievalService(workingMemoryRepository, episodicRepository" in host
        and "proceduralRepository" in host,
        "F-03 promotion and normal CoALA retrieval must share the same procedural repository")
require("RecoveryProcedurePromotionService recoveryProcedurePromotion" in runtime,
        "full graph runtime must expose the F-03 promotion/retrieval boundary")

pg_store = read("supply-chain-agent-infrastructure-postgres/src/main/java/dev/supplychain/agent/infrastructure/postgres/memory/PostgresRecoveryProcedureArtifactStore.java")
for token in ("ON CONFLICT (artifact_sha256) DO NOTHING", "sha256(payload)", "codec.decode(payload)",
              "urn:sha256:", "MemoryScopeId expectedScope", "artifact.scopeId().equals(expectedScope)"):
    require(token in pg_store, f"F-03 PostgreSQL content-addressed artifact store lacks {token}")
runner = read("supply-chain-agent-infrastructure-postgres/src/main/java/dev/supplychain/agent/infrastructure/postgres/migration/PostgresMigrationRunner.java")
require('new Migration(12, "b2_immutable_recovery_procedure_artifacts"' in runner,
        "V012 immutable B2 artifact migration must be registered as migration 12")
v012 = read("supply-chain-agent-infrastructure-postgres/src/main/resources/db/migration/V012__b2_immutable_recovery_procedure_artifacts.sql")
require("CREATE TABLE coala_recovery_procedure_artifact" in v012 and "payload bytea NOT NULL" in v012,
        "V012 must store immutable typed artifact payloads")

promotion_tests = read("supply-chain-agent-mechanisms/src/test/java/dev/supplychain/agent/mechanisms/b2/RecoveryProcedurePromotionServiceTest.java")
for token in ("b2ApprovalStagesOnlyADraftAndIndependentProceduralLifecycleIsRequired",
              "approvedRecoveryProcedureBecomesAStandardCoalaB1RetrievalCandidate",
              "proceduralReviewerAndApproverMustBeIndependentActors",
              "laterRetrievalRejectsChangedActionDescriptorAndForeignScope"):
    require(token in promotion_tests, f"F-03 required lifecycle/retrieval test is missing: {token}")

# F-04: compare bounded alternative coalitions before admission; never choose from a truncated search.
policy = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/b3/WorkspaceCoalitionCompetitionPolicy.java")
require("maximumCoalitionSize > 6" in policy and "maximumEvaluatedCoalitionProposals > 10_000" in policy,
        "F-04 coalition search bounds must remain enforced")
proposal = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/b3/WorkspaceCoalitionProposal.java")
require("candidateIds" in proposal and "newlyCoveredTags" in proposal and "totalScore" in proposal,
        "F-04 first-class alternative coalition proposals must retain their membership, coverage and score")
admission = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/b3/WorkspaceAdmissionService.java")
for token in ("evaluateCoalitions", "enumerate", "withSelected", "coalitionSearchTruncated",
              "singleton fallback", "new WorkspaceCoalitionProposal"):
    require(token in admission, f"F-04 coalition competition/fallback path is missing: {token}")
snapshot = read("supply-chain-agent-mechanisms/src/main/java/dev/supplychain/agent/mechanisms/b3/WorkspaceSnapshot.java")
require("admitted coalition has no matching selected competition proposal" in snapshot,
        "F-04 admitted coalition must reconcile to the selected proposal trace")
workspace_tests = read("supply-chain-agent-mechanisms/src/test/java/dev/supplychain/agent/mechanisms/b3/WorkspaceAdmissionServiceTest.java")
for token in ("alternativeCoalitionsCompeteAsFirstClassOptionsAndRecordTheWinner",
              "incompleteCoalitionEnumerationFallsBackToSingletonAdmissionAndReportsTruncation",
              "coalitionCompetitionIsDeterministicAcrossCandidateInputOrder",
              "coalitionGenerationNeverCombinesConflictingCandidates"):
    require(token in workspace_tests, f"F-04 required competition test is missing: {token}")

if FAILURES:
    print("M8-PreB7 F-03/F-04 static verification: FAILED", file=sys.stderr)
    for failure in FAILURES:
        print(f" - {failure}", file=sys.stderr)
    raise SystemExit(1)

print("M8-PreB7 F-03 B2-to-CoALA procedural-memory governance and retrieval: PASS")
print("M8-PreB7 F-03 content-addressed PostgreSQL artifact/V012 composition: PASS")
print("M8-PreB7 F-04 bounded singleton/coalition competition and truncation fallback: PASS")
print("M8-PreB7 F-04 snapshot trace invariants and test coverage: PASS")
print("No claim of automatic procedure execution, full LIDA reproduction or adaptive empirical advantage.")
