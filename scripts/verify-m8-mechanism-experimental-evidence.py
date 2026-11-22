#!/usr/bin/env python3
"""Fail-closed static guard for mechanism-specific training artifacts and independent experiment evidence."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "supply-chain-agent-evaluation/src/main/java/dev/supplychain/agent/evaluation/experiment/mechanism"
EXEC = ROOT / "supply-chain-agent-evaluation/src/main/java/dev/supplychain/agent/evaluation/experiment/execution"
HOST = ROOT / "supply-chain-agent-evaluation/src/main/java/dev/supplychain/agent/evaluation/host"
FACTORY = ROOT / "supply-chain-agent-evaluation/src/main/java/dev/supplychain/agent/evaluation/experiment/scenario/M8ExperimentScenarioFactory.java"

required = [
    "MechanismTrainingArtifactBuilder.java", "MechanismTrainingArtifactFingerprints.java",
    "MechanismTrainingArtifactVerifier.java", "MechanismTrainingArtifactBundle.java",
    "MechanismSpecificExperimentHarness.java", "MechanismSpecificExperimentBatchRunner.java",
    "MechanismSpecificExperimentRunVerifier.java", "FullGraphMechanismEvidenceCollector.java",
    "IndependentSimulatorPlanVerifier.java", "MechanismSpecificExperimentSessionFactory.java",
]
for name in required:
    assert (JAVA / name).is_file(), f"missing experiment evidence source: {name}"

builder = (JAVA / "MechanismTrainingArtifactBuilder.java").read_text(encoding="utf-8")
fingerprints = (JAVA / "MechanismTrainingArtifactFingerprints.java").read_text(encoding="utf-8")
verifier = (JAVA / "MechanismTrainingArtifactVerifier.java").read_text(encoding="utf-8")
bundle = (JAVA / "MechanismTrainingArtifactBundle.java").read_text(encoding="utf-8")
harness = (JAVA / "MechanismSpecificExperimentHarness.java").read_text(encoding="utf-8")
plan = (JAVA / "MechanismExperimentPlanFactory.java").read_text(encoding="utf-8")
collector = (JAVA / "FullGraphMechanismEvidenceCollector.java").read_text(encoding="utf-8")
session = (EXEC / "FullGraphExperimentSession.java").read_text(encoding="utf-8")
adapter = (EXEC / "FullGraphExperimentAdapter.java").read_text(encoding="utf-8")
run_verifier = (JAVA / "MechanismSpecificExperimentRunVerifier.java").read_text(encoding="utf-8")
factory = FACTORY.read_text(encoding="utf-8")

checks = {
    "one synthetic shared research scope is versioned in the scenario factory":
        'EXPERIMENT_MEMORY_SCOPE_ID = "tenant:m8-experiment:shared-research-v2"' in factory,
    "builder binds exact full preregistered TRAIN set":
        "MechanismTrainingArtifactBundle.exactTrainUnitFingerprints()" in builder and "requireTrainSubset" in builder,
    "builder re-verifies complete bundle before returning":
        "artifactVerifier.verify(bundle);" in builder,
    "B1 training uses actual initial and final feature snapshots":
        "initialFeatureStates" in builder and "featureSeeds" in builder and "B1MemoryFeatureState" in fingerprints,
    "B1 source outcomes must be verifier-attributed and cover independent units":
        "outcomes from at least two distinct TRAIN units" in builder and "b1OutcomeVerifier().verify(outcome)" in verifier,
    "B2 treatment requires at least one approved reusable TRAIN procedure":
        "input.proceduresByCondition().isEmpty()" in builder and "B2 treatment requires at least one approved reusable TRAIN procedure" in builder,
    "B2 procedures must be registered typed artifacts in the shared scope":
        "ReusableRecoveryProcedure" in builder and "b2ProcedureVerifier().verify(procedure, now)" in verifier,
    "B2 source evidence must cover every declared TRAIN source unit":
        "referencedUnits.equals(units)" in verifier,
    "B3 builder only accepts verifier-minted coalition outcome capabilities":
        "List<VerifiedWorkspaceCoalitionOutcomeEvidence>" in builder and "B3 cannot be trained without verifier-minted" in builder,
    "B3 candidate remains SHADOW and exact source ledger is reconstructed":
        "Disposition.SHADOW" in builder and "preference adjustments cannot be reproduced" in verifier,
    "B3 source rows use stable canonical ordering":
        "canonicalCoalitionOutcome" in verifier and "new TreeSet<>(value.candidateIds())" in fingerprints,
    "B6 builder requires simulator labels and creates TRAINED-only profile":
        "B6 requires independently produced simulator label evidence" in builder and "Status.TRAINED" in builder,
    "B6 has at least 100 distinct source units and label samples per dimension":
        "sourceUnits.size() < 100" in builder and "samples.size() < 100" in verifier,
    "all four child fingerprints bind complete source content":
        all(f"MechanismTrainingArtifactFingerprints.{name}(bundle.{field}())" in verifier for name, field in
            [("b1", "b1"), ("b2", "b2"), ("b3", "b3"), ("b6", "b6")]),
    "artifact bundle excludes every non-TRAIN unit":
        "exactTrainUnitFingerprints()" in bundle and "requireSubset" in bundle,
    "external verifiers have no permissive defaults":
        "B1 independent outcome verifier is required" in verifier and "B2 independent procedure verifier is required" in verifier
        and "B3 independent coalition outcome verifier is required" in verifier and "B6 simulator label verifier is required" in verifier,
    "HOLDOUT harness re-verifies training artifacts before constructing graph sessions":
        harness.index("trainingArtifactVerifier.verify(trainingArtifacts)") < harness.index("sessionFactory.create(scenario, plan, trainingArtifacts)"),
    "HOLDOUT plan factory accepts only registered, train-disjoint HOLDOUT units":
        "unit.split() != ExperimentSplit.HOLDOUT" in plan and "bundle.trainUnitFingerprints().contains(unit.effectiveInputFingerprint())" in plan,
    "adapter has distinct TRAIN/HOLDOUT authorizations and a verified HOLDOUT factory":
        "authorizedSplit" in adapter and "forFrozenTrainingArtifactHoldout" in adapter and "runHoldoutPair" in adapter,
    "B1 session snapshots are captured before execution":
        "initialB1MemoryFeatureStates" in session and "initialB1MemoryFeatureStates()" in collector,
    "B2 evidence reads exact procedures from the graph PlanningRequest":
        "planning.request().reusableRecoveryProcedures()" in collector and "isReusableRecoveryAction" in collector,
    "B4 objective fingerprint is invariant across paired graph execution":
        "objectiveFingerprint" in collector and "objectiveFingerprint" in run_verifier,
    "independent simulator plan replay uses a separate simulator factory":
        "IndependentSimulatorReplayFactory" in (JAVA / "IndependentSimulatorPlanVerifier.java").read_text(encoding="utf-8")
        and "replayFactory.create(session)" in (JAVA / "IndependentSimulatorPlanVerifier.java").read_text(encoding="utf-8"),
    "graph is not resumed and no approval is dispatched":
        "GraphInput.resume()" not in adapter and "externalActionDispatch" in run_verifier,
    "no real-world business outcome claim is emitted":
        "realWorldOutcomeClaimsAllowed\\\":false" in (JAVA / "MechanismSpecificExperimentBatchRunner.java").read_text(encoding="utf-8"),
}

for label, passed in checks.items():
    print(("PASS" if passed else "FAIL") + ": " + label)
if not all(checks.values()):
    raise SystemExit(f"{sum(not value for value in checks.values())} mechanism/evidence guards failed")

# Protocol metadata must be explicit about the new version and evidence boundary.
registry = (ROOT / "docs/research/M8-GAP01-GAP05-EXPERIMENT-REGISTRY.yaml").read_text(encoding="utf-8")
assert "M8-GAP01-05-IEP-1.1" in registry
assert "tenant:m8-experiment:shared-research-v2" in registry
assert "confirmatory_harness_api_available: true" in registry
assert "confirmatory_runs_executed: false" in registry
assert "empirical_effectiveness_claims_allowed: false" in registry
metadata = json.loads((ROOT / "docs/research/CONFORMANCE_ASSESSMENT_METADATA.json").read_text(encoding="utf-8"))
assert metadata["experiment_protocol"]["protocol_id"] == "M8-GAP01-05-IEP-1.1"
assert metadata["experiment_protocol"]["confirmatory_harness_available"] is True
print(f"PASS: {len(checks)} mechanism and independent evidence invariants")
print("PASS: protocol/registry metadata alignment and no confirmatory results claimed")
