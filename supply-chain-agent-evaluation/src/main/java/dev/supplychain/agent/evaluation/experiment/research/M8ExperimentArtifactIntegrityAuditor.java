package dev.supplychain.agent.evaluation.experiment.research;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentTrainingBatchRunner;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;
import dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Re-reads TRAIN JSONL artifacts from disk and verifies exact unit coverage and content hashes. */
public final class M8ExperimentArtifactIntegrityAuditor {
    private static final Pattern UNIT_ID = Pattern.compile("\\\"experimentUnitId\\\":\\\"([^\\\"]+)\\\"");
    private static final Pattern UNIT_FINGERPRINT = Pattern.compile("\\\"effectiveInputFingerprint\\\":\\\"([0-9a-f]{64})\\\"");

    public AuditResult auditFiveTrainBatches(
            Map<ExperimentMechanism, FullGraphExperimentTrainingBatchRunner.BatchResult> supplied,
            String candidateCodeFingerprint) throws IOException {
        Objects.requireNonNull(supplied, "supplied");
        String candidate = sha(candidateCodeFingerprint, "candidateCodeFingerprint");
        if (!supplied.keySet().equals(Set.of(ExperimentMechanism.values())))
            throw new SecurityException("TRAIN integrity audit requires exactly the five preregistered mechanism batches");
        List<ExperimentScenarioUnit> expected = M8ExperimentScenarioFactory.loadDefault().generate(ExperimentSplit.TRAIN);
        Map<String, String> expectedUnits = expected.stream().collect(Collectors.toMap(
                ExperimentScenarioUnit::experimentUnitId, ExperimentScenarioUnit::effectiveInputFingerprint));
        // Must match FullGraphExperimentTrainingBatchRunner: sorted experimentUnitId values,
        // not the effective-input fingerprints. The per-ID fingerprint mapping is checked below.
        String expectedUnitSetFingerprint = sha256(String.join("\n", expectedUnits.keySet().stream().sorted().toList()));
        var records = new EnumMap<ExperimentMechanism, ArtifactRecord>(ExperimentMechanism.class);
        var paths = new java.util.HashSet<Path>();
        for (ExperimentMechanism mechanism : ExperimentMechanism.values()) {
            FullGraphExperimentTrainingBatchRunner.BatchResult batch = supplied.get(mechanism);
            if (batch == null || !paths.add(batch.outputFile().toAbsolutePath().normalize()))
                throw new SecurityException("TRAIN mechanisms must have distinct, non-null artifact files");
            ArtifactRecord record = auditOne(batch.outputFile(), batch.artifactSha256(),
                    batch.pairRecordsSha256(), batch.unitSetFingerprint(), expectedUnitSetFingerprint,
                    expectedUnits, mechanism);
            records.put(mechanism, record);
        }
        return new AuditResult(candidate, M8ExperimentScenarioFactory.PROTOCOL_ID,
                M8ExperimentScenarioFactory.GENERATOR_ID, ExperimentSplit.TRAIN, expected.size(),
                records, sha256(canonicalArtifactRecords(records)));
    }

    public void revalidate(AuditResult audit) throws IOException {
        Objects.requireNonNull(audit, "audit");
        if (audit.split() != ExperimentSplit.TRAIN || audit.unitCount() != 280
                || !audit.protocolId().equals(M8ExperimentScenarioFactory.PROTOCOL_ID)
                || !audit.generatorId().equals(M8ExperimentScenarioFactory.GENERATOR_ID)
                || audit.artifacts().size() != ExperimentMechanism.values().length)
            throw new SecurityException("artifact audit receipt is not the exact locked TRAIN run");
        List<ExperimentScenarioUnit> expected = M8ExperimentScenarioFactory.loadDefault().generate(ExperimentSplit.TRAIN);
        Map<String, String> expectedUnits = expected.stream().collect(Collectors.toMap(
                ExperimentScenarioUnit::experimentUnitId, ExperimentScenarioUnit::effectiveInputFingerprint));
        String expectedUnitSetFingerprint = sha256(String.join("\n", expectedUnits.keySet().stream().sorted().toList()));
        if (!audit.auditFingerprint().equals(sha256(canonicalArtifactRecords(audit.artifacts()))))
            throw new SecurityException("TRAIN audit receipt fingerprint does not reproduce its artifact manifest");
        for (ExperimentMechanism mechanism : ExperimentMechanism.values()) {
            ArtifactRecord record = audit.artifacts().get(mechanism);
            if (record == null || record.mechanism() != mechanism
                    || !record.unitSetFingerprint().equals(expectedUnitSetFingerprint))
                throw new SecurityException("TRAIN audit receipt is missing a mechanism or exact unit-set fingerprint");
            ArtifactRecord actual = auditOne(Path.of(record.path()), record.artifactSha256(), record.pairRecordsSha256(),
                    record.unitSetFingerprint(), expectedUnitSetFingerprint, expectedUnits, mechanism);
            if (actual.byteCount() != record.byteCount()
                    || !actual.artifactSha256().equals(record.artifactSha256())
                    || !actual.pairRecordsSha256().equals(record.pairRecordsSha256())
                    || actual.pairCount() != record.pairCount()
                    || !actual.evidenceKind().equals(record.evidenceKind()))
                throw new SecurityException("TRAIN audit record does not reproduce from the current artifact bytes");
        }
    }


    static String canonicalArtifactRecords(Map<ExperimentMechanism, ArtifactRecord> artifacts) {
        return artifacts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    ArtifactRecord value = entry.getValue();
                    return entry.getKey() + "|" + value.path() + "|" + value.pairCount() + "|"
                            + value.unitSetFingerprint() + "|" + value.artifactSha256() + "|"
                            + value.pairRecordsSha256() + "|" + value.byteCount() + "|" + value.evidenceKind();
                }).collect(Collectors.joining("\n"));
    }

    private ArtifactRecord auditOne(Path file, String expectedArtifactHash, String expectedPairHash,
            String resultUnitSetFingerprint, String expectedUnitSetFingerprint,
            Map<String, String> expectedUnits, ExperimentMechanism mechanism) throws IOException {
        Path path = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IOException("TRAIN evidence artifact is not a regular file: " + path);
        if (!sha(expectedArtifactHash, "expectedArtifactHash").equals(sha256(Files.readAllBytes(path))))
            throw new SecurityException("TRAIN evidence artifact bytes differ from the runner's SHA-256: " + path);
        try (var files = Files.list(path.getParent())) {
            if (files.anyMatch(candidate -> candidate.getFileName().toString().endsWith(".pending")))
                throw new SecurityException("TRAIN output directory contains an unfinished .pending artifact");
        }
        String content = Files.readString(path, StandardCharsets.UTF_8);
        if (!content.endsWith("\n")) throw new SecurityException("TRAIN JSONL artifact must end with a newline");
        int separator = content.indexOf('\n');
        if (separator <= 0) throw new SecurityException("TRAIN artifact is missing its JSONL header");
        String header = content.substring(0, separator);
        String body = content.substring(separator + 1);
        String pairHash = sha256(body);
        if (!pairHash.equals(sha(expectedPairHash, "expectedPairHash")))
            throw new SecurityException("TRAIN pair-record bytes do not match their recorded SHA-256");
        if (!sha(resultUnitSetFingerprint, "resultUnitSetFingerprint").equals(expectedUnitSetFingerprint))
            throw new SecurityException("TRAIN unit-set fingerprint differs from the locked scenario register");
        requireHeader(header, "recordType", "fullGraphTrainingBatch");
        requireHeader(header, "protocolId", M8ExperimentScenarioFactory.PROTOCOL_ID);
        requireHeader(header, "generatorId", M8ExperimentScenarioFactory.GENERATOR_ID);
        requireHeader(header, "split", "TRAIN");
        requireHeader(header, "mechanism", mechanism.name());
        requireHeader(header, "unitCount", "280");
        requireHeader(header, "unitSetFingerprint", expectedUnitSetFingerprint);
        requireHeader(header, "pairRecordsSha256", pairHash);
        requireHeader(header, "evidenceKind", "FULL_GRAPH_TELEMETRY_NO_VERIFIED_OUTCOME_LABELS");
        if (!header.contains("\"confirmatoryInferenceAllowed\":false"))
            throw new SecurityException("TRAIN telemetry header must explicitly forbid confirmatory inference");

        List<String> lines = body.lines().toList();
        if (lines.size() != 280) throw new SecurityException("TRAIN pair JSONL body must contain exactly 280 records");
        var actual = new TreeMap<String, String>();
        for (String line : lines) {
            if (!line.startsWith("{") || !line.endsWith("}") || !line.contains("\"recordType\":\"fullGraphMatchedPair\""))
                throw new SecurityException("TRAIN body contains a malformed or unexpected record type");
            String unitId = capture(UNIT_ID, line, "experimentUnitId");
            String fingerprint = capture(UNIT_FINGERPRINT, line, "effectiveInputFingerprint");
            if (actual.putIfAbsent(unitId, fingerprint) != null)
                throw new SecurityException("TRAIN artifact contains a duplicate episode ID");
        }
        if (!actual.equals(expectedUnits))
            throw new SecurityException("TRAIN artifact body does not contain the exact preregistered unit/fingerprint mapping");
        return new ArtifactRecord(mechanism, ExperimentSplit.TRAIN, path.toString(), 280,
                expectedUnitSetFingerprint, sha256(Files.readAllBytes(path)), pairHash,
                Files.size(path), "FULL_GRAPH_TELEMETRY_NO_VERIFIED_OUTCOME_LABELS");
    }

    private static void requireHeader(String header, String key, String expected) {
        String token = "\"" + key + "\":\"" + expected + "\"";
        if (key.equals("unitCount")) token = "\"" + key + "\":" + expected;
        if (!header.contains(token)) throw new SecurityException("TRAIN JSONL header field mismatch: " + key);
    }

    private static String capture(Pattern pattern, String input, String name) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) throw new SecurityException("TRAIN pair JSONL record is missing " + name);
        return matcher.group(1);
    }

    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private static String sha(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (!result.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        return result;
    }

    public record ArtifactRecord(ExperimentMechanism mechanism, ExperimentSplit split, String path,
            int pairCount, String unitSetFingerprint, String artifactSha256, String pairRecordsSha256,
            long byteCount, String evidenceKind) {
        public ArtifactRecord {
            mechanism = Objects.requireNonNull(mechanism, "mechanism");
            split = Objects.requireNonNull(split, "split");
            path = Objects.requireNonNull(path, "path");
            if (pairCount != 280 || byteCount < 1) throw new IllegalArgumentException("invalid TRAIN artifact dimensions");
            unitSetFingerprint = sha(unitSetFingerprint, "unitSetFingerprint");
            artifactSha256 = sha(artifactSha256, "artifactSha256");
            pairRecordsSha256 = sha(pairRecordsSha256, "pairRecordsSha256");
            evidenceKind = Objects.requireNonNull(evidenceKind, "evidenceKind");
            if (!"FULL_GRAPH_TELEMETRY_NO_VERIFIED_OUTCOME_LABELS".equals(evidenceKind))
                throw new SecurityException("TRAIN telemetry may not be promoted to outcome evidence");
        }
    }

    public record AuditResult(String candidateCodeFingerprint, String protocolId, String generatorId,
            ExperimentSplit split, int unitCount, Map<ExperimentMechanism, ArtifactRecord> artifacts,
            String auditFingerprint) {
        public AuditResult {
            candidateCodeFingerprint = sha(candidateCodeFingerprint, "candidateCodeFingerprint");
            protocolId = Objects.requireNonNull(protocolId, "protocolId");
            generatorId = Objects.requireNonNull(generatorId, "generatorId");
            split = Objects.requireNonNull(split, "split");
            if (split != ExperimentSplit.TRAIN || unitCount != 280) throw new SecurityException("audit must represent exact TRAIN set");
            var copy = new EnumMap<ExperimentMechanism, ArtifactRecord>(ExperimentMechanism.class);
            copy.putAll(Objects.requireNonNull(artifacts, "artifacts"));
            if (!copy.keySet().equals(Set.of(ExperimentMechanism.values())))
                throw new IllegalArgumentException("audit must contain five mechanism artifacts");
            artifacts = Map.copyOf(copy);
            auditFingerprint = sha(auditFingerprint, "auditFingerprint");
        }
    }
}
