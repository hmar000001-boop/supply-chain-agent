package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Executes the exact 280-unit confirmatory HOLDOUT split and atomically seals verified pair evidence. */
public final class MechanismSpecificExperimentBatchRunner {
    public static final int EXPECTED_HOLDOUT_UNITS = 280;
    private final MechanismSpecificExperimentHarness harness;

    public MechanismSpecificExperimentBatchRunner(MechanismSpecificExperimentHarness harness) {
        this.harness = Objects.requireNonNull(harness, "harness");
    }

    public BatchResult run(List<ExperimentScenarioUnit> suppliedUnits, ExperimentMechanism mechanism,
            Path outputFile) throws Exception {
        Objects.requireNonNull(suppliedUnits, "suppliedUnits");
        Objects.requireNonNull(mechanism, "mechanism");
        Objects.requireNonNull(outputFile, "outputFile");
        List<ExperimentScenarioUnit> expected = M8ExperimentScenarioFactory.loadDefault().generate(ExperimentSplit.HOLDOUT);
        var expectedById = expected.stream().collect(java.util.stream.Collectors.toMap(
                ExperimentScenarioUnit::experimentUnitId, unit -> unit));
        List<ExperimentScenarioUnit> actual = List.copyOf(suppliedUnits);
        if (actual.size() != EXPECTED_HOLDOUT_UNITS
                || actual.stream().map(ExperimentScenarioUnit::experimentUnitId).distinct().count() != EXPECTED_HOLDOUT_UNITS
                || !actual.stream().map(ExperimentScenarioUnit::experimentUnitId).collect(java.util.stream.Collectors.toSet())
                    .equals(expectedById.keySet()))
            throw new SecurityException("confirmatory run must contain the exact 280 preregistered HOLDOUT unit IDs");
        for (ExperimentScenarioUnit unit : actual) {
            ExperimentScenarioUnit registered = expectedById.get(unit.experimentUnitId());
            if (unit.split() != ExperimentSplit.HOLDOUT || !unit.protocolId().equals(M8ExperimentScenarioFactory.PROTOCOL_ID)
                    || registered == null || !unit.effectiveInputFingerprint().equals(registered.effectiveInputFingerprint())
                    || !unit.fixture().fixtureFingerprint().equals(registered.fixture().fixtureFingerprint())
                    || unit.seed() != registered.seed() || !unit.parameters().equals(registered.parameters()))
                throw new SecurityException("supplied HOLDOUT unit differs from the preregistered generated fixture");
        }
        List<ExperimentScenarioUnit> ordered = actual.stream()
                .sorted(java.util.Comparator.comparing(ExperimentScenarioUnit::experimentUnitId)).toList();
        var pairs = new java.util.ArrayList<MechanismSpecificExperimentPair>(EXPECTED_HOLDOUT_UNITS);
        for (ExperimentScenarioUnit unit : ordered) pairs.add(harness.runHoldoutPair(unit, mechanism));
        B6HoldoutEvaluationReport calibrationReport = mechanism == ExperimentMechanism.B6_UNCERTAINTY_CALIBRATION
                ? B6HoldoutEvaluationReport.fromPairs(pairs) : null;
        String pairBody = pairs.stream().map(MechanismSpecificExperimentPair::toJsonLine)
                .collect(java.util.stream.Collectors.joining("\n", "", "\n"));
        String unitSetFingerprint = sha256(String.join("\n", expected.stream()
                .map(ExperimentScenarioUnit::effectiveInputFingerprint).sorted().toList()));
        String header = "{\"recordType\":\"mechanismSpecificHoldoutBatch\",\"protocolId\":" + q(M8ExperimentScenarioFactory.PROTOCOL_ID)
                + ",\"generatorId\":" + q(M8ExperimentScenarioFactory.GENERATOR_ID)
                + ",\"split\":\"HOLDOUT\",\"mechanism\":" + q(mechanism.name())
                + ",\"unitCount\":" + pairs.size() + ",\"unitSetFingerprint\":" + q(unitSetFingerprint)
                + ",\"candidateCodeFingerprint\":" + q(harness.trainingArtifacts().candidateCodeFingerprint())
                + ",\"trainingArtifactFingerprint\":" + q(harness.trainingArtifacts().artifactFingerprint())
                + ",\"evidenceKind\":\"INDEPENDENT_SIMULATOR_AND_TYPED_MECHANISM_EVIDENCE\""
                + ",\"realWorldOutcomeClaimsAllowed\":false}\n";
        String report = calibrationReport == null ? "" : "{\"recordType\":\"b6HoldoutCalibrationReport\",\"report\":"
                + calibrationReport.toJson() + "}\n";
        String content = header + pairBody + report;
        writeAtomicallyWithoutOverwrite(outputFile, content);
        return new BatchResult(outputFile.toAbsolutePath().normalize(), pairs.size(),
                sha256(content), unitSetFingerprint, calibrationReport);
    }

    private static void writeAtomicallyWithoutOverwrite(Path output, String content) throws IOException {
        Path absolute = output.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) throw new IllegalArgumentException("output path must have a parent directory");
        Files.createDirectories(parent);
        if (Files.exists(absolute)) throw new IOException("refusing to overwrite existing evidence artifact: " + absolute);
        Path temporary = Files.createTempFile(parent, absolute.getFileName().toString(), ".pending");
        boolean moved = false;
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, absolute); }
            moved = true;
        } finally { if (!moved) Files.deleteIfExists(temporary); }
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private static String q(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    public record BatchResult(Path outputFile, int pairCount, String artifactSha256,
            String unitSetFingerprint, B6HoldoutEvaluationReport b6CalibrationReport) {
        public BatchResult {
            outputFile = Objects.requireNonNull(outputFile, "outputFile");
            if (pairCount != EXPECTED_HOLDOUT_UNITS) throw new IllegalArgumentException("complete HOLDOUT batch must contain 280 pairs");
            artifactSha256 = ExperimentEvidenceFingerprint.requireSha(artifactSha256, "artifactSha256");
            unitSetFingerprint = ExperimentEvidenceFingerprint.requireSha(unitSetFingerprint, "unitSetFingerprint");
        }
    }
}
