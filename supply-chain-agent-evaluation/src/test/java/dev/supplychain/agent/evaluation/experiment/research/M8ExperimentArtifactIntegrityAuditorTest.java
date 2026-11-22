package dev.supplychain.agent.evaluation.experiment.research;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentTrainingBatchRunner;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;
import dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class M8ExperimentArtifactIntegrityAuditorTest {
    @TempDir Path tempDir;

    @Test
    void auditsAndRevalidatesExactFiveBatchesUsingRunnerUnitIdFingerprintCanonicalization() throws Exception {
        var batches = createSyntheticBatches();
        var auditor = new M8ExperimentArtifactIntegrityAuditor();
        var result = auditor.auditFiveTrainBatches(batches, "a".repeat(64));
        assertEquals(280, result.unitCount());
        assertEquals(5, result.artifacts().size());
        auditor.revalidate(result);
    }

    @Test
    void revalidationRejectsAnyPostAuditArtifactMutation() throws Exception {
        var batches = createSyntheticBatches();
        var auditor = new M8ExperimentArtifactIntegrityAuditor();
        var result = auditor.auditFiveTrainBatches(batches, "b".repeat(64));
        Path changed = batches.get(ExperimentMechanism.values()[0]).outputFile();
        Files.writeString(changed, Files.readString(changed) + " ");
        assertThrows(SecurityException.class, () -> auditor.revalidate(result));
    }

    private EnumMap<ExperimentMechanism, FullGraphExperimentTrainingBatchRunner.BatchResult> createSyntheticBatches()
            throws Exception {
        List<ExperimentScenarioUnit> units = M8ExperimentScenarioFactory.loadDefault().generate(ExperimentSplit.TRAIN)
                .stream().sorted(java.util.Comparator.comparing(ExperimentScenarioUnit::experimentUnitId)).toList();
        String unitSet = M8ExperimentArtifactIntegrityAuditor.sha256(units.stream()
                .map(ExperimentScenarioUnit::experimentUnitId).collect(Collectors.joining("\n")));
        var result = new EnumMap<ExperimentMechanism, FullGraphExperimentTrainingBatchRunner.BatchResult>(ExperimentMechanism.class);
        for (ExperimentMechanism mechanism : ExperimentMechanism.values()) {
            String body = units.stream().map(unit -> "{\"recordType\":\"fullGraphMatchedPair\",\"experimentUnitId\":\""
                    + unit.experimentUnitId() + "\",\"effectiveInputFingerprint\":\""
                    + unit.effectiveInputFingerprint() + "\"}").collect(Collectors.joining("\n", "", "\n"));
            String pairsHash = M8ExperimentArtifactIntegrityAuditor.sha256(body);
            String header = "{\"recordType\":\"fullGraphTrainingBatch\",\"protocolId\":\""
                    + M8ExperimentScenarioFactory.PROTOCOL_ID + "\",\"generatorId\":\""
                    + M8ExperimentScenarioFactory.GENERATOR_ID + "\",\"split\":\"TRAIN\",\"mechanism\":\""
                    + mechanism.name() + "\",\"unitCount\":280,\"unitSetFingerprint\":\"" + unitSet
                    + "\",\"pairRecordsSha256\":\"" + pairsHash
                    + "\",\"evidenceKind\":\"FULL_GRAPH_TELEMETRY_NO_VERIFIED_OUTCOME_LABELS\",\"confirmatoryInferenceAllowed\":false}\n";
            Path file = tempDir.resolve(mechanism.name() + ".jsonl");
            String content = header + body;
            Files.writeString(file, content);
            result.put(mechanism, new FullGraphExperimentTrainingBatchRunner.BatchResult(file, 280,
                    M8ExperimentArtifactIntegrityAuditor.sha256(content), pairsHash, unitSet));
        }
        return result;
    }
}
