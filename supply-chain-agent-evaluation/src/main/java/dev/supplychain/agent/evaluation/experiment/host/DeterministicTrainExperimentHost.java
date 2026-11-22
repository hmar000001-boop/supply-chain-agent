package dev.supplychain.agent.evaluation.experiment.host;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentAdapter;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentTrainingBatchRunner;
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
import java.time.Instant;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Composition root for the primary deterministic M8 TRAIN telemetry pass. This host is
 * TRAIN-only and emits graph telemetry, not verified outcome labels or a frozen learning bundle.
 * It deliberately has no method that opens HOLDOUT or TRANSFER.
 */
public final class DeterministicTrainExperimentHost {
    private final String candidateCodeFingerprint;
    private final DeterministicTrainSessionFactory sessionFactory;
    private final DeterministicTrainExecutionProfile executionProfile;
    private final FullGraphExperimentTrainingBatchRunner batchRunner;

    public DeterministicTrainExperimentHost(String candidateCodeFingerprint,
            DeterministicTrainPortsFactory portsFactory) {
        this(candidateCodeFingerprint, new ComposedDeterministicTrainSessionFactory(portsFactory));
    }

    public DeterministicTrainExperimentHost(String candidateCodeFingerprint,
            DeterministicTrainSessionFactory sessionFactory) {
        this.candidateCodeFingerprint = Objects.requireNonNull(candidateCodeFingerprint, "candidateCodeFingerprint").trim();
        if (!this.candidateCodeFingerprint.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("candidateCodeFingerprint must be lowercase SHA-256");
        this.sessionFactory = Objects.requireNonNull(sessionFactory, "sessionFactory");
        this.executionProfile = Objects.requireNonNull(sessionFactory.executionProfile(), "executionProfile");
        this.executionProfile.requireAdmissibleForTrain();
        var adapter = new FullGraphExperimentAdapter(sessionFactory);
        this.batchRunner = new FullGraphExperimentTrainingBatchRunner(adapter);
    }

    /**
     * Runs the exact preregistered 280-unit TRAIN split for all five mechanisms. Each mechanism
     * receives its own immutable JSONL output. The run manifest is marked RUNNING before execution,
     * COMPLETED only after all five batches are sealed, and FAILED if any batch throws.
     */
    public HostRunResult runAll(Path outputDirectory) throws Exception {
        Path directory = Objects.requireNonNull(outputDirectory, "outputDirectory").toAbsolutePath().normalize();
        if (Files.exists(directory))
            throw new IOException("refusing to reuse an existing TRAIN output directory: " + directory);
        Path parent = directory.getParent();
        if (parent == null) throw new IOException("TRAIN output directory must not be a filesystem root");
        Files.createDirectories(parent);
        Files.createDirectory(directory);

        Path statusFile = directory.resolve("train-run-status.json");
        Instant startedAt = Instant.now();
        writeNew(statusFile, statusJson("RUNNING", startedAt, null, Map.of()));

        var results = new EnumMap<ExperimentMechanism, FullGraphExperimentTrainingBatchRunner.BatchResult>(
                ExperimentMechanism.class);
        ExperimentMechanism activeMechanism = null;
        try {
            List<dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit> units =
                    M8ExperimentScenarioFactory.loadDefault().generate(ExperimentSplit.TRAIN);
            for (ExperimentMechanism mechanism : ExperimentMechanism.values()) {
                activeMechanism = mechanism;
                Path output = directory.resolve(mechanism.name().toLowerCase(java.util.Locale.ROOT) + ".jsonl");
                var result = batchRunner.run(units, mechanism, output);
                results.put(mechanism, result);
                replaceAtomically(statusFile, statusJson("RUNNING", startedAt,
                        mechanism.name(), results));
            }
            Instant completedAt = Instant.now();
            String completed = statusJson("COMPLETED", startedAt, null, results);
            replaceAtomically(statusFile, completed);
            return new HostRunResult(directory, candidateCodeFingerprint, executionProfile.profileId(),
                    results, sha256(completed), startedAt, completedAt);
        } catch (Exception failure) {
            String failed = statusJson("FAILED", startedAt,
                    activeMechanism == null ? null : activeMechanism.name(), results,
                    failure.getClass().getName() + ": " + String.valueOf(failure.getMessage()));
            try { replaceAtomically(statusFile, failed); }
            catch (IOException statusFailure) { failure.addSuppressed(statusFailure); }
            throw failure;
        }
    }

    private String statusJson(String status, Instant startedAt, String activeMechanism,
            Map<ExperimentMechanism, FullGraphExperimentTrainingBatchRunner.BatchResult> results) {
        return statusJson(status, startedAt, activeMechanism, results, null);
    }

    private String statusJson(String status, Instant startedAt, String activeMechanism,
            Map<ExperimentMechanism, FullGraphExperimentTrainingBatchRunner.BatchResult> results,
            String failure) {
        StringBuilder json = new StringBuilder(1024);
        json.append('{')
                .append("\"recordType\":\"m8DeterministicTrainHostRun\"")
                .append(",\"protocolId\":").append(q(M8ExperimentScenarioFactory.PROTOCOL_ID))
                .append(",\"generatorId\":").append(q(M8ExperimentScenarioFactory.GENERATOR_ID))
                .append(",\"candidateCodeFingerprint\":").append(q(candidateCodeFingerprint))
                .append(",\"executionProfile\":").append(q(executionProfile.profileId()))
                .append(",\"requestInterpreterId\":").append(q(executionProfile.requestInterpreterId()))
                .append(",\"reasoningProfileId\":").append(q(executionProfile.reasoningProfileId()))
                .append(",\"plannerProfileId\":").append(q(executionProfile.plannerProfileId()))
                .append(",\"worldSimulatorProfileId\":").append(q(executionProfile.worldSimulatorProfileId()))
                .append(",\"modelExecution\":").append(q(executionProfile.modelExecution().name()))
                .append(",\"deterministicRequestInterpretation\":").append(executionProfile.deterministicRequestInterpretation())
                .append(",\"deterministicReasoning\":").append(executionProfile.deterministicReasoning())
                .append(",\"deterministicPlanning\":").append(executionProfile.deterministicPlanning())
                .append(",\"deterministicWorldSimulation\":").append(executionProfile.deterministicWorldSimulation())
                .append(",\"externalConnectorsDisabled\":").append(executionProfile.externalConnectorsDisabled())
                .append(",\"productionCredentialsAbsent\":").append(executionProfile.productionCredentialsAbsent())
                .append(",\"split\":\"TRAIN\"")
                .append(",\"expectedUnitCount\":280")
                .append(",\"status\":").append(q(status))
                .append(",\"startedAt\":").append(q(startedAt.toString()))
                .append(",\"manifestUpdatedAt\":").append(q(Instant.now().toString()));
        if (activeMechanism != null) json.append(",\"activeMechanism\":").append(q(activeMechanism));
        if (failure != null) json.append(",\"failure\":").append(q(failure));
        json.append(",\"evidenceKind\":\"FULL_GRAPH_TELEMETRY_NO_VERIFIED_OUTCOME_LABELS\"")
                .append(",\"confirmatoryInferenceAllowed\":false")
                .append(",\"holdoutOpened\":false")
                .append(",\"transferOpened\":false")
                .append(",\"mechanismBatches\":[");
        boolean first = true;
        for (ExperimentMechanism mechanism : ExperimentMechanism.values()) {
            var result = results.get(mechanism);
            if (result == null) continue;
            if (!first) json.append(',');
            first = false;
            json.append('{')
                    .append("\"mechanism\":").append(q(mechanism.name()))
                    .append(",\"pairCount\":").append(result.pairCount())
                    .append(",\"artifactSha256\":").append(q(result.artifactSha256()))
                    .append(",\"pairRecordsSha256\":").append(q(result.pairRecordsSha256()))
                    .append(",\"unitSetFingerprint\":").append(q(result.unitSetFingerprint()))
                    .append('}');
        }
        return json.append("]}").append('\n').toString();
    }

    private static void writeNew(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void replaceAtomically(Path target, String content) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".pending");
        boolean moved = false;
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String q(String value) {
        Objects.requireNonNull(value, "JSON string value");
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('\"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '\"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.append('\"').toString();
    }

    public String candidateCodeFingerprint() { return candidateCodeFingerprint; }
    public DeterministicTrainExecutionProfile executionProfile() { return executionProfile; }

    public record HostRunResult(Path outputDirectory, String candidateCodeFingerprint,
            String executionProfileId,
            Map<ExperimentMechanism, FullGraphExperimentTrainingBatchRunner.BatchResult> mechanismResults,
            String runManifestSha256, Instant startedAt, Instant completedAt) {
        public HostRunResult {
            outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory").toAbsolutePath().normalize();
            candidateCodeFingerprint = Objects.requireNonNull(candidateCodeFingerprint, "candidateCodeFingerprint").trim();
            if (!candidateCodeFingerprint.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("candidateCodeFingerprint must be lowercase SHA-256");
            executionProfileId = Objects.requireNonNull(executionProfileId, "executionProfileId");
            var copiedResults = new EnumMap<ExperimentMechanism, FullGraphExperimentTrainingBatchRunner.BatchResult>(ExperimentMechanism.class);
            copiedResults.putAll(Objects.requireNonNull(mechanismResults, "mechanismResults"));
            mechanismResults = Map.copyOf(copiedResults);
            if (mechanismResults.size() != ExperimentMechanism.values().length
                    || mechanismResults.values().stream().anyMatch(value -> value.pairCount() != 280))
                throw new IllegalArgumentException("completed deterministic TRAIN host must contain five complete 280-pair batches");
            runManifestSha256 = Objects.requireNonNull(runManifestSha256, "runManifestSha256").trim();
            if (!runManifestSha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("runManifestSha256 must be lowercase SHA-256");
            startedAt = Objects.requireNonNull(startedAt, "startedAt");
            completedAt = Objects.requireNonNull(completedAt, "completedAt");
            if (completedAt.isBefore(startedAt)) throw new IllegalArgumentException("completedAt precedes startedAt");
        }
    }
}
