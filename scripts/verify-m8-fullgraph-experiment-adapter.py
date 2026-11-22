#!/usr/bin/env python3
"""Static fail-closed checks for M8's full-graph experiment adapter contract."""
from pathlib import Path
import csv
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "supply-chain-agent-evaluation/src/main/java/dev/supplychain/agent/evaluation/experiment/execution"
HOST = ROOT / "supply-chain-agent-evaluation/src/main/java/dev/supplychain/agent/evaluation/host"
TEST = ROOT / "supply-chain-agent-evaluation/src/test/java/dev/supplychain/agent/evaluation/M8B4FullGraphWorkflowTest.java"

required = [
    "ExperimentArm.java", "ExperimentMechanism.java", "ExperimentSandboxAttestation.java",
    "ExperimentSandboxAudit.java", "FullGraphExperimentSession.java", "FullGraphExperimentSessionFactory.java",
    "FullGraphExperimentAdapter.java", "FullGraphExperimentRunObservation.java",
    "FullGraphExperimentPair.java", "FullGraphExperimentTelemetry.java",
    "FullGraphExperimentTrainingBatchRunner.java",
]
for name in required:
    if not (BASE / name).is_file():
        raise SystemExit(f"FAIL: missing adapter source {name}")

adapter = (BASE / "FullGraphExperimentAdapter.java").read_text(encoding="utf-8")
session = (BASE / "FullGraphExperimentSession.java").read_text(encoding="utf-8")
safety = (BASE / "ExperimentSandboxAttestation.java").read_text(encoding="utf-8")
batch = (BASE / "FullGraphExperimentTrainingBatchRunner.java").read_text(encoding="utf-8")
mechanism_batch = (ROOT / "supply-chain-agent-evaluation/src/main/java/dev/supplychain/agent/evaluation/experiment/mechanism/MechanismSpecificExperimentBatchRunner.java").read_text(encoding="utf-8")
host = (HOST / "FullGraphEvaluationHost.java").read_text(encoding="utf-8")
runtime = (HOST / "FullGraphRuntime.java").read_text(encoding="utf-8")
test = TEST.read_text(encoding="utf-8")

checks = {
    "TRAIN and verified HOLDOUT are split-gated separately": "authorizedSplit" in adapter and "forFrozenTrainingArtifactHoldout" in adapter and "unit.split() != requiredSplit" in adapter,
    "full graph stream is invoked": ".graph().stream(GraphInput.args(session.graphInput()), session.config())" in adapter,
    "graph is never resumed": "GraphInput.resume()" not in adapter,
    "approval/authorization input rejected": "normalized.contains(\"approval\")" in session and "authorizationresponse" in session,
    "external effect counters enforced": "auditDelta.isSideEffectFree()" in adapter,
    "sandbox attestation is fail-closed": "!inMemoryOnly || !externalConnectorsDisabled" in safety,
    "baseline and treatment receive separate graph instances": "requireDifferentReference(baseline.runtime().graph(), treatment.runtime().graph()" in adapter,
    "stateful B1 repository isolation is checked": all(token in adapter for token in ("B1 feature repository", "B1 feature learning service", "B1 retrieval selector")),
    "per-arm side-effect audit instances are isolated": "sandbox audit counters" in adapter and "sandbox attestations" in adapter,
    "matched graph input maps are identical": '"graph input map"' in adapter,
    "active and persisted B6 calibration are rejected": "rejectCalibratedB6State" in adapter and "CalibrationStatus.CALIBRATED" in adapter,
    "observation fingerprint excludes ephemeral session ID": 'session.sessionId() + "|" + session.unit()' not in adapter and "conditionFingerprint" in adapter,
    "B2 and B3 repositories are checked when configured": "B2 draft repository" in adapter and "B3 outcome repository" in adapter,
    "unit identity is matched byte-for-byte to preregistered fixture": all(token in adapter for token in (
        "preregisteredUnits.get(unit.experimentUnitId())", "registered.fixture().fixtureFingerprint().equals(unit.fixture().fixtureFingerprint())",
        "registered.effectiveInputFingerprint().equals(unit.effectiveInputFingerprint())")),
    "telemetry-only training batch requires exact 280 TRAIN units": "EXPECTED_TRAIN_UNITS = 280" in batch and "exact 280 preregistered TRAIN unit IDs" in batch,
    "training telemetry batch explicitly disallows confirmatory inference": "confirmatoryInferenceAllowed\\\":false" in batch,
    "mechanism batch requires exact 280 preregistered HOLDOUT units": "EXPECTED_HOLDOUT_UNITS = 280" in mechanism_batch and "exact 280 preregistered HOLDOUT unit IDs" in mechanism_batch,
    "mechanism HOLDOUT output forbids real-world outcome claims": "realWorldOutcomeClaimsAllowed\\\":false" in mechanism_batch and "INDEPENDENT_SIMULATOR_AND_TYPED_MECHANISM_EVIDENCE" in mechanism_batch,
    "host exposes B1 instrumentation": "b1FeatureRepository" in host and "b1MemoryFeatureRepository" in runtime,
    "host supports optional B6 lifecycle injection": "UncertaintyCalibrationService uncertaintyCalibration" in host,
    "real full graph pair smoke test exists": "fullGraphExperimentAdapterRunsMatchedIsolatedTrainingArmsWithoutDispatch" in test,
    "holdout is rejected before graph creation": "fullGraphExperimentAdapterRejectsHoldoutBeforeCreatingAnyGraphSession" in test,
}
failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    raise SystemExit(f"{len(failed)} full-graph experiment adapter checks failed")
print(f"PASS: {len(checks)} full-graph experiment adapter checks")
