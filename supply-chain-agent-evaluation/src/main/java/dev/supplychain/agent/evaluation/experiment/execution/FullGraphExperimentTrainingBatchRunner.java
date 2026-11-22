package dev.supplychain.agent.evaluation.experiment.execution;

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

/** Executes the complete preregistered TRAIN split and atomically writes raw full-graph telemetry. */
public final class FullGraphExperimentTrainingBatchRunner {
    public static final int EXPECTED_TRAIN_UNITS = 280;
    private final FullGraphExperimentAdapter adapter;

    public FullGraphExperimentTrainingBatchRunner(FullGraphExperimentAdapter adapter) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
    }

    /**
     * Runs exactly the 280 preregistered TRAIN units for one mechanism. Partial or hand-selected
     * batches are deliberately rejected so the output cannot be mistaken for the locked training set.
     * The output contains graph observations only; it is not a verified outcome-label dataset.
     */
    public BatchResult run(List<ExperimentScenarioUnit> suppliedUnits, ExperimentMechanism mechanism,
                           Path outputFile) throws Exception {
        Objects.requireNonNull(suppliedUnits, "suppliedUnits");
        Objects.requireNonNull(mechanism, "mechanism");
        Objects.requireNonNull(outputFile, "outputFile");
        List<ExperimentScenarioUnit> expected = M8ExperimentScenarioFactory.loadDefault().generate(ExperimentSplit.TRAIN);
        List<String> expectedIds = expected.stream().map(ExperimentScenarioUnit::experimentUnitId).sorted().toList();
        List<ExperimentScenarioUnit> actual = List.copyOf(suppliedUnits);
        List<String> actualIds = actual.stream().map(ExperimentScenarioUnit::experimentUnitId).sorted().toList();
        if (actual.size() != EXPECTED_TRAIN_UNITS || !expectedIds.equals(actualIds)) {
            throw new SecurityException("training batch must contain the exact 280 preregistered TRAIN unit IDs");
        }
        if (actual.stream().anyMatch(unit -> unit.split() != ExperimentSplit.TRAIN
                || !M8ExperimentScenarioFactory.PROTOCOL_ID.equals(unit.protocolId()))) {
            throw new SecurityException("training batch contains a non-TRAIN or foreign-protocol unit");
        }
        var expectedById = expected.stream().collect(java.util.stream.Collectors.toMap(
                ExperimentScenarioUnit::experimentUnitId, unit -> unit));
        for (ExperimentScenarioUnit unit : actual) {
            ExperimentScenarioUnit registered = expectedById.get(unit.experimentUnitId());
            if (registered == null || !registered.fixture().fixtureFingerprint().equals(unit.fixture().fixtureFingerprint())
                    || !registered.effectiveInputFingerprint().equals(unit.effectiveInputFingerprint())
                    || registered.seed() != unit.seed() || !registered.parameters().equals(unit.parameters())) {
                throw new SecurityException("training batch unit differs from the preregistered generated fixture: "
                        + unit.experimentUnitId());
            }
        }
        List<ExperimentScenarioUnit> ordered = actual.stream()
                .sorted(java.util.Comparator.comparing(ExperimentScenarioUnit::experimentUnitId)).toList();
        var pairs = new java.util.ArrayList<FullGraphExperimentPair>(EXPECTED_TRAIN_UNITS);
        for (ExperimentScenarioUnit unit : ordered) pairs.add(adapter.runTrainingPair(unit, mechanism));
        String body = pairs.stream().map(FullGraphExperimentPair::toJsonLine)
                .collect(java.util.stream.Collectors.joining("\n", "", "\n"));
        String bodyFingerprint = sha256(body);
        String unitSetCanonical = String.join("\n", expectedIds);
        String header = "{\"recordType\":\"fullGraphTrainingBatch\",\"protocolId\":"
                + FullGraphExperimentRunObservation.quote(M8ExperimentScenarioFactory.PROTOCOL_ID)
                + ",\"generatorId\":" + FullGraphExperimentRunObservation.quote(M8ExperimentScenarioFactory.GENERATOR_ID)
                + ",\"split\":\"TRAIN\",\"mechanism\":"
                + FullGraphExperimentRunObservation.quote(mechanism.name())
                + ",\"unitCount\":" + pairs.size()
                + ",\"unitSetFingerprint\":" + FullGraphExperimentRunObservation.quote(sha256(unitSetCanonical))
                + ",\"pairRecordsSha256\":" + FullGraphExperimentRunObservation.quote(bodyFingerprint)
                + ",\"evidenceKind\":\"FULL_GRAPH_TELEMETRY_NO_VERIFIED_OUTCOME_LABELS\",\"confirmatoryInferenceAllowed\":false}\n";
        writeAtomicallyWithoutOverwrite(outputFile, header + body);
        return new BatchResult(outputFile.toAbsolutePath().normalize(), pairs.size(),
                sha256(header + body), bodyFingerprint, sha256(unitSetCanonical));
    }

    private static void writeAtomicallyWithoutOverwrite(Path output, String content) throws IOException {
        Path absolute = output.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) throw new IllegalArgumentException("output path must have a parent directory");
        Files.createDirectories(parent);
        if (Files.exists(absolute)) throw new IOException("refusing to overwrite existing experiment artifact: " + absolute);
        Path temporary = Files.createTempFile(parent, absolute.getFileName().toString(), ".pending");
        boolean moved = false;
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, absolute);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }

    public record BatchResult(Path outputFile, int pairCount, String artifactSha256,
                              String pairRecordsSha256, String unitSetFingerprint) {
        public BatchResult {
            outputFile = Objects.requireNonNull(outputFile, "outputFile");
            if (pairCount != EXPECTED_TRAIN_UNITS) throw new IllegalArgumentException("complete TRAIN result must contain 280 pairs");
            artifactSha256 = required(artifactSha256, "artifactSha256");
            pairRecordsSha256 = required(pairRecordsSha256, "pairRecordsSha256");
            unitSetFingerprint = required(unitSetFingerprint, "unitSetFingerprint");
        }
        private static String required(String value, String name) {
            String result = Objects.requireNonNull(value, name).trim();
            if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
            return result;
        }
    }
}
