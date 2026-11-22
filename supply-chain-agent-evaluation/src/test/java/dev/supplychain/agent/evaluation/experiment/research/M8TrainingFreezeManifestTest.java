package dev.supplychain.agent.evaluation.experiment.research;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M8TrainingFreezeManifestTest {
    @Test
    void manifestBindsCiSourceAuditArchiveAndNeverOpensHoldoutOrTransfer() {
        String sha = "a".repeat(64);
        var artifacts = new EnumMap<ExperimentMechanism, M8ExperimentArtifactIntegrityAuditor.ArtifactRecord>(ExperimentMechanism.class);
        for (ExperimentMechanism mechanism : ExperimentMechanism.values()) {
            artifacts.put(mechanism, new M8ExperimentArtifactIntegrityAuditor.ArtifactRecord(
                    mechanism, ExperimentSplit.TRAIN, "/evidence/" + mechanism + ".jsonl", 280,
                    sha, sha, sha, 4096, "FULL_GRAPH_TELEMETRY_NO_VERIFIED_OUTCOME_LABELS"));
        }
        String canonical = M8ExperimentArtifactIntegrityAuditor.canonicalArtifactRecords(artifacts);
        var audit = new M8ExperimentArtifactIntegrityAuditor.AuditResult(sha,
                dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory.PROTOCOL_ID,
                dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory.GENERATOR_ID,
                ExperimentSplit.TRAIN, 280, artifacts, M8ExperimentArtifactIntegrityAuditor.sha256(canonical));
        var safetyCounters = new M8ExperimentSafetyEvidence.ArmCounters(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        var safetyByMechanism = new EnumMap<ExperimentMechanism, M8ExperimentSafetyEvidence.MechanismCounters>(ExperimentMechanism.class);
        for (ExperimentMechanism mechanism : ExperimentMechanism.values())
            safetyByMechanism.put(mechanism, new M8ExperimentSafetyEvidence.MechanismCounters(safetyCounters, safetyCounters));
        var trainingSafety = M8ExperimentSafetyEvidence.create(ExperimentSplit.TRAIN, 280, safetyByMechanism,
                "independent-train-safety-v1", "e".repeat(64));
        var stored = new M8TrainingArtifactArchive.StoredArtifact("b".repeat(64) + ".m8bundle", sha,
                "b".repeat(64), 8192, "canonical-json-v1");
        var manifest = M8TrainingFreezeManifest.create(sha, "c".repeat(40),
                "https://github.com/acme/repository/actions/runs/12345", "12345", "SUCCESS", true, "d".repeat(64),
                "trusted-ci-source-verifier-v1", trainingSafety, audit, stored, "typed-bundle-verifier-v1",
                Instant.parse("2026-11-22T17:00:00Z"));
        String json = manifest.toJson();
        assertFalse(manifest.holdoutAccessOpened());
        assertFalse(manifest.transferAccessOpened());
        assertTrue(json.contains("\"holdoutAccessOpened\":false"));
        assertTrue(json.contains("\"transferAccessOpened\":false"));
        assertTrue(json.contains(manifest.manifestFingerprint()));
        assertTrue(json.contains("trusted-ci-source-verifier-v1"));
        assertTrue(json.contains(trainingSafety.evidenceFingerprint()));
        assertTrue(json.contains("independent-train-safety-v1"));
        assertTrue(json.contains("\"ciConclusion\":\"SUCCESS\""));
        assertTrue(json.contains("\"gitWorkingTreeClean\":true"));
    }
}
