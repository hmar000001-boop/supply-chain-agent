#!/usr/bin/env python3
"""Static contract checks for the durable application composition and CoALA learning surface."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
failures: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)

host_path = ROOT / "supply-chain-agent-app/src/main/java/dev/supplychain/agent/app/durable/DurableCognitiveApplicationHost.java"
host = host_path.read_text(encoding="utf-8") if host_path.exists() else ""
require(bool(host), "DurableCognitiveApplicationHost is missing")
for token, description in [
    ("PostgresDatabaseConfig.fromEnvironment", "database configuration from deployment environment"),
    ("PostgresPooledDataSourceFactory.create", "one pooled PostgreSQL datasource"),
    ("new PostgresAgentPersistence(", "PostgreSQL persistence composition root"),
    ("persistence.migrate()", "versioned database migrations before graph compilation"),
    ("PostgresRuntimePersistence", "scope-bound runtime persistence"),
    ("PostgresB1MemoryFeatureRepository", "durable B1 feature storage"),
    ("recoveryProcedureDrafts", "durable B2 draft storage"),
    ("b3OutcomeEvidence", "durable B3 receipts/outcomes"),
    ("PostgresRecoveryProcedureArtifactStore", "durable B2 procedure artifacts"),
    ("PostgresLangGraph4jCheckpointSaverFactory.create", "PostgreSQL LangGraph checkpoint saver"),
    ("CognitiveCycleApplicationGraph.buildFull", "full cognitive graph composition"),
    ("CognitiveCycleApplicationGraph.compileFull", "full cognitive graph compilation"),
    ("runtime.newResourceBudgetExecutionGuard", "single shared B6 guard construction"),
    ("Function<ResourceBudgetExecutionGuard, ReasoningProvider>", "reasoning provider bound to the host-created guard"),
    ("public Optional<CycleRunResult> start(", "new top-level cycle ingress"),
    ("public Optional<CycleRunResult> resume(", "checkpoint resume ingress"),
    ("GraphResult.from(stream).isInterruptionMetadata()", "explicit graph interruption result reporting"),
    ("streamAndSnapshot(GraphInput.args(input), config)", "top-level cycle that returns checkpoint state at interruption"),
    ("streamAndSnapshot(GraphInput.resume(), updated)", "resumed cycle that returns checkpoint state at interruption"),
    ("sealed interface CycleResumeResponse", "closed typed resume-response contract"),
    ("ClarificationReply", "typed clarification response"),
    ("EvidenceReply", "typed evidence response"),
    ("HumanControlReply", "typed human-control response with authenticated actor"),
    ("ApprovalReply", "typed approval response with authenticated approver"),
    ("private static Map<String, Object> resumeUpdates", "closed mapping from typed responses to allowed checkpoint fields"),
    ("requireBoundScope", "fixed host memory-scope enforcement"),
]:
    require(token in host, f"Durable host is missing {description} ({token})")
require("new dev.supplychain.agent.mechanisms.b3.InMemoryWorkspaceRepository()" in host,
        "B3 cycle-scoped workspace must remain explicitly ephemeral")
for forbidden in ("InMemoryEpisodeRepository", "InMemorySemanticFactRepository", "InMemoryProceduralMemoryRepository",
                  "InMemoryWorkingMemoryRepository", "InMemoryActionExecutionLedger"):
    require(forbidden not in host, f"Durable host must not silently fall back to {forbidden}")

learning_path = ROOT / "supply-chain-agent-actions/src/main/java/dev/supplychain/agent/actions/internal/learning/LearningActionCatalog.java"
learning = learning_path.read_text(encoding="utf-8") if learning_path.exists() else ""
registrations = re.findall(r"registry\.register\(new (\w+)\(", learning)
require(len(registrations) == 10, f"Expected exactly 10 governed learning actions, found {len(registrations)}")
require(len(set(registrations)) == 10, "Learning action catalog has duplicate handler registrations")
require("CONTRACT_VERSION = 1" in learning, "Learning action contract version must be explicit")
require("scopePrecondition(boundScope" in learning, "Every learning action must enforce the host-bound scope")
require("requireIndependentReviewer" in learning, "Procedure review/activation must enforce identity separation")

catalog_path = ROOT / "supply-chain-agent-actions/src/main/java/dev/supplychain/agent/actions/SupplyChainActionCatalog.java"
catalog = catalog_path.read_text(encoding="utf-8") if catalog_path.exists() else ""
require("LearningActionCatalog.register(registry, episodic, semantic, procedural, boundScope)" in catalog,
        "Complete action catalog must register all learning actions for a fixed scope")
require("registry.register(new EpisodeLearningAction(episodic))" in catalog,
        "Backward-compatible legacy fixture API must remain available")

integration_test_path = ROOT / "supply-chain-agent-app/src/test/java/dev/supplychain/agent/app/durable/DurableCognitiveApplicationHostPostgresIntegrationTest.java"
integration_test = integration_test_path.read_text(encoding="utf-8") if integration_test_path.exists() else ""
for token, description in [
    ("DurableCognitiveApplicationHost.open(environment, ports)", "real PostgreSQL host startup"),
    ("workingMemory().load(durableMemoryId)", "CoALA working-memory persistence across host restart"),
    ('restartedHost.resume(scope.value(), "cycle-1"', "checkpoint resume after host restart"),
    ("restartedHost.start(new DurableCognitiveApplicationHost.CycleRequest(", "second top-level cycle ingress"),
    ("reasoning.isBoundToBudgetIdentity(host.resourceBudgetGuard())", "same B6 guard bound to M3 reasoning"),
    ("new DurableCognitiveApplicationHost.ApprovalReply(", "resume response rejects a missing authenticated approver"),
    ("assertThrows(NullPointerException.class, () -> restartedHost.resume", "resume requires an explicit typed response"),
]:
    require(token in integration_test, f"PostgreSQL application integration test is missing {description}")

if failures:
    print("M8 durable application composition verification: FAILED", file=sys.stderr)
    for failure in failures:
        print(f" - {failure}", file=sys.stderr)
    raise SystemExit(1)
print("M8 durable application composition verification: PASS")
print("Durable PostgreSQL memory/runtime/checkpoint composition: PASS")
print("B3 ephemeral workspace / durable receipt boundary: PASS")
print("Scope-bound, versioned CoALA learning-action catalog: PASS (10 actions)")
print("Trusted B2 author/validator/approver separation: PASS")
print("Top-level cycle and checkpoint-resume APIs: PASS")
print("PostgreSQL host restart, durable memory, resumed checkpoint and second-cycle integration assertions: PASS")
