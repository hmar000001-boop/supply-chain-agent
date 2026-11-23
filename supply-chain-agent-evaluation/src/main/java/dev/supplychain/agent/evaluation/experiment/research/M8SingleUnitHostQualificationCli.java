package dev.supplychain.agent.evaluation.experiment.research;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentAdapter;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentPair;
import dev.supplychain.agent.evaluation.experiment.execution.FullGraphExperimentRunObservation;
import dev.supplychain.agent.evaluation.experiment.host.ComposedDeterministicTrainSessionFactory;
import dev.supplychain.agent.evaluation.experiment.host.DeterministicTrainExecutionProfile;
import dev.supplychain.agent.evaluation.experiment.host.ScenarioFixtureDeterministicTrainPortsFactory;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentScenarioUnit;
import dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit;
import dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * One-unit, five-mechanism qualification of the actual LangGraph4j TRAIN host behind the trusted
 * CI freeze boundary. It is a preflight only: no outcome labels, learning bundle, bulk TRAIN file,
 * HOLDOUT admission or TRANSFER admission is produced.
 */
public final class M8SingleUnitHostQualificationCli {
    private static final String BUNDLE_VERIFIER_ID = "m8-bundle-verifier-v1";
    private static final String[] ISSUERS = {
            "m8-endpoint-v1", "m8-safety-v1", "m8-ci-v1", "m8-b1-v1",
            "m8-b2-v1", "m8-b3-v1", "m8-b6-v1"
    };
    private static final Set<String> CONTEXT_KEYS = Set.of("schema", "candidateCodeFingerprint", "gitCommit",
            "ciWorkflowUrl", "ciRunId", "ciConclusion", "hostedCiGreen", "gitWorkingTreeClean",
            "dependencyManifestFingerprint", "bundleVerifierId", "ciAttestationSubject");

    private M8SingleUnitHostQualificationCli() { }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArguments(args);
        Path repositoryRoot = Path.of(required(options, "repo-root")).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(repositoryRoot) || !Files.isDirectory(repositoryRoot))
            throw new SecurityException("repository root must be an existing real directory");
        repositoryRoot = repositoryRoot.toRealPath();
        Path contextFile = Path.of(required(options, "ci-freeze-context")).toAbsolutePath().normalize();
        Path publicKeys = Path.of(required(options, "pinned-public-key-dir")).toAbsolutePath().normalize();
        Path attestations = Path.of(required(options, "attestation-dir")).toAbsolutePath().normalize();
        Path trainingArchive = Path.of(required(options, "training-archive-dir")).toAbsolutePath().normalize();
        Path output = Path.of(required(options, "output")).toAbsolutePath().normalize();
        Path targetRoot = repositoryRoot.resolve("target").normalize();
        if (Files.isSymbolicLink(targetRoot))
            throw new SecurityException("repository target/ directory cannot be a symbolic link");
        if (!output.startsWith(targetRoot) || output.equals(targetRoot) || output.getParent() == null)
            throw new SecurityException("qualification report must be written under the repository target/ directory");
        Path relativeParent = targetRoot.relativize(output.getParent());
        Path cursor = targetRoot;
        for (Path segment : relativeParent) {
            cursor = cursor.resolve(segment);
            if (Files.isSymbolicLink(cursor))
                throw new SecurityException("qualification report path cannot traverse a symbolic link: " + cursor);
        }
        Files.createDirectories(output.getParent());
        Path realTargetRoot = targetRoot.toRealPath();
        Path realOutput = output.getParent().toRealPath().resolve(output.getFileName()).normalize();
        if (!realOutput.startsWith(realTargetRoot) || realOutput.equals(realTargetRoot))
            throw new SecurityException("qualification report path resolves outside repository target/");
        output = realOutput;
        contextFile = requireExternalPath(repositoryRoot, contextFile, "CI freeze context");
        publicKeys = requireExternalPath(repositoryRoot, publicKeys, "pinned public keys");
        attestations = requireExternalPath(repositoryRoot, attestations, "attestation repository");
        trainingArchive = requireExternalPath(repositoryRoot, trainingArchive, "TRAIN artifact archive");

        M8TrainingArtifactFreezeService.FreezeContext context = readFreezeContext(contextFile);
        if (!BUNDLE_VERIFIER_ID.equals(context.bundleVerifierId()))
            throw new SecurityException("CI freeze context uses an unexpected bundle verifier identity");

        var configuration = new M8TrustedResearchComposition.Configuration(repositoryRoot, publicKeys,
                attestations, trainingArchive, ISSUERS[0], ISSUERS[1], ISSUERS[2], ISSUERS[3], ISSUERS[4],
                ISSUERS[5], ISSUERS[6], "m8-git-ci-freeze-verifier-v1", BUNDLE_VERIFIER_ID,
                Clock.systemUTC(), Duration.ofDays(30));
        M8TrustedResearchComposition trusted = M8TrustedResearchComposition.open(configuration);

        // The signed CI attestation binds the checked-out commit, workflow run, code tree and POM tree.
        trusted.freezePrerequisiteVerifier().verify(context);
        Path ciAttestationFile = attestations.resolve("M8_CI_FREEZE--"
                + GitCiM8FreezePrerequisiteVerifier.attestationSubject(context) + ".attestation");
        if (Files.isSymbolicLink(ciAttestationFile) || !Files.isRegularFile(ciAttestationFile)
                || Files.size(ciAttestationFile) > 32 * 1024)
            throw new SecurityException("verified CI attestation record is absent, unsafe or oversized");
        String ciAttestationRecordFingerprint = M8CandidateCodeFingerprint.sha256(Files.readAllBytes(ciAttestationFile));

        List<ExperimentScenarioUnit> registeredTrain = M8ExperimentScenarioFactory.loadDefault()
                .generate(ExperimentSplit.TRAIN);
        ExperimentScenarioUnit qualificationUnit = registeredTrain.stream()
                .filter(unit -> "MULTI_ALTERNATIVE".equals(unit.fixtureProfile()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("registered TRAIN set has no MULTI_ALTERNATIVE qualification fixture"));

        var sessionFactory = new ComposedDeterministicTrainSessionFactory(
                new ScenarioFixtureDeterministicTrainPortsFactory());
        DeterministicTrainExecutionProfile executionProfile = sessionFactory.executionProfile();
        executionProfile.requireAdmissibleForTrain();
        FullGraphExperimentAdapter adapter = new FullGraphExperimentAdapter(sessionFactory);
        List<FullGraphExperimentPair> pairs = new ArrayList<>();
        Set<String> sessionIds = new HashSet<>();
        for (ExperimentMechanism mechanism : ExperimentMechanism.values()) {
            FullGraphExperimentPair pair = adapter.runTrainingPair(qualificationUnit, mechanism);
            validatePair(pair, qualificationUnit);
            if (!sessionIds.add(pair.baseline().sessionId()) || !sessionIds.add(pair.treatment().sessionId()))
                throw new SecurityException("full-graph qualification reused a session identity across matched arms or mechanisms");
            pairs.add(pair);
        }
        if (pairs.size() != ExperimentMechanism.values().length)
            throw new SecurityException("not every registered M8 mechanism passed the one-unit qualification");

        String report = reportJson(context, trusted.pinnedIssuerKeyFingerprints(),
                ciAttestationRecordFingerprint, qualificationUnit, executionProfile, pairs);
        writeNewAtomically(output, report);
        System.out.println("M8_C1_C2_HOST_QUALIFICATION_PASS");
        System.out.println("qualificationReport=" + output);
        System.out.println("mechanismPairs=" + pairs.size());
        System.out.println("qualificationOnly=true; trainEvidence=false; verifiedOutcomeLabels=0; holdoutOpened=false; transferOpened=false");
    }

    private static Path requireExternalPath(Path repositoryRoot, Path candidate, String label) throws IOException {
        Path absolute = candidate.toAbsolutePath().normalize();
        if (absolute.startsWith(repositoryRoot))
            throw new SecurityException(label + " must be stored outside the Git checkout: " + absolute);
        if (Files.exists(absolute, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(absolute))
                throw new SecurityException(label + " cannot be a symbolic link: " + absolute);
            Path resolved = absolute.toRealPath();
            if (resolved.startsWith(repositoryRoot))
                throw new SecurityException(label + " resolves inside the Git checkout: " + resolved);
            return resolved;
        }
        Path existingAncestor = absolute.getParent();
        while (existingAncestor != null
                && !Files.exists(existingAncestor, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            existingAncestor = existingAncestor.getParent();
        }
        if (existingAncestor == null)
            throw new IOException("cannot resolve an existing parent for external " + label + ": " + absolute);
        Path resolvedAncestor = existingAncestor.toRealPath();
        Path resolvedCandidate = resolvedAncestor.resolve(existingAncestor.relativize(absolute)).normalize();
        if (resolvedCandidate.startsWith(repositoryRoot))
            throw new SecurityException(label + " resolves inside the Git checkout: " + resolvedCandidate);
        return resolvedCandidate;
    }

    static M8TrainingArtifactFreezeService.FreezeContext readFreezeContext(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file))
            throw new SecurityException("CI freeze context must be a regular non-symlink file");
        if (Files.size(file) > 16 * 1024) throw new SecurityException("CI freeze context is oversized");
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        Set<String> actual = properties.stringPropertyNames();
        if (!actual.equals(CONTEXT_KEYS))
            throw new SecurityException("CI freeze context keys differ from the required versioned schema; expected="
                    + CONTEXT_KEYS + ", found=" + actual);
        if (!"M8-C1-FREEZE-CONTEXT-V1".equals(properties.getProperty("schema")))
            throw new SecurityException("unsupported CI freeze context schema");
        if (!"SUCCESS".equals(properties.getProperty("ciConclusion"))
                || !"true".equals(properties.getProperty("hostedCiGreen"))
                || !"true".equals(properties.getProperty("gitWorkingTreeClean")))
            throw new SecurityException("CI freeze context does not record a successful hosted run and clean tree");
        var context = new M8TrainingArtifactFreezeService.FreezeContext(
                properties.getProperty("candidateCodeFingerprint"), properties.getProperty("gitCommit"),
                properties.getProperty("ciWorkflowUrl"), properties.getProperty("ciRunId"),
                properties.getProperty("ciConclusion"), Boolean.parseBoolean(properties.getProperty("hostedCiGreen")),
                Boolean.parseBoolean(properties.getProperty("gitWorkingTreeClean")),
                properties.getProperty("dependencyManifestFingerprint"), properties.getProperty("bundleVerifierId"));
        String expectedSubject = GitCiM8FreezePrerequisiteVerifier.attestationSubject(context);
        if (!expectedSubject.equals(properties.getProperty("ciAttestationSubject")))
            throw new SecurityException("CI attestation subject in the context file does not match its canonical fields");
        return context;
    }

    static void validatePair(FullGraphExperimentPair pair, ExperimentScenarioUnit unit) {
        Objects.requireNonNull(pair, "pair");
        Objects.requireNonNull(unit, "unit");
        if (!M8ExperimentScenarioFactory.PROTOCOL_ID.equals(pair.protocolId())
                || !unit.experimentUnitId().equals(pair.experimentUnitId())
                || !unit.effectiveInputFingerprint().equals(pair.effectiveInputFingerprint())
                || !unit.fixture().fixtureFingerprint().equals(pair.fixtureFingerprint())) {
            throw new SecurityException("qualification pair is not bound to the preregistered exact simulator input");
        }
        if (pair.baseline().mechanism() != pair.mechanism() || pair.treatment().mechanism() != pair.mechanism()
                || pair.baseline().arm() != dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm.BASELINE
                || pair.treatment().arm() != dev.supplychain.agent.evaluation.experiment.execution.ExperimentArm.TREATMENT
                || pair.baseline().sessionId().equals(pair.treatment().sessionId())
                || !pair.baseline().requestFingerprint().equals(pair.treatment().requestFingerprint())) {
            throw new SecurityException("qualification pair lost matched-input or per-arm isolation invariants");
        }
        validateObservation(pair.baseline(), unit.experimentUnitId(), unit.effectiveInputFingerprint());
        validateObservation(pair.treatment(), unit.experimentUnitId(), unit.effectiveInputFingerprint());
    }

    static void validateObservation(FullGraphExperimentRunObservation observation,
            String expectedUnitId, String expectedInputFingerprint) {
        Objects.requireNonNull(observation, "observation");
        if (!expectedUnitId.equals(observation.experimentUnitId())
                || !expectedInputFingerprint.equals(observation.effectiveInputFingerprint()))
            throw new SecurityException("qualification observation does not match the exact registered unit input");
        if (observation.boundary() != FullGraphExperimentRunObservation.Boundary.APPROVAL)
            throw new SecurityException("qualification graph did not stop at the required approval boundary: "
                    + observation.boundary());
        if (!observation.sideEffectDelta().isSideEffectFree())
            throw new SecurityException("qualification graph observed a prohibited effect");
        if (!"true".equals(observation.stateSignals().get("approvalIdPresent")))
            throw new SecurityException("qualification approval boundary has no bound approval identity");
        requireMetric(observation, "runtime.action.dispatch.attempts", 0.0, true);
        requireMetric(observation, "runtime.simulated.state.effects", 0.0, true);
        requireMetric(observation, "runtime.approval.request.count", 1.0, false);
    }

    private static void requireMetric(FullGraphExperimentRunObservation observation,
            String name, double expectedOrMinimum, boolean exact) {
        Double value = observation.metrics().get(name);
        if (value == null || (exact ? Double.compare(value, expectedOrMinimum) != 0 : value < expectedOrMinimum))
            throw new SecurityException("qualification metric failed closed: " + name + "=" + value);
    }

    private static String reportJson(M8TrainingArtifactFreezeService.FreezeContext context,
            Map<String, String> issuerKeyFingerprints, String ciAttestationRecordFingerprint,
            ExperimentScenarioUnit unit, DeterministicTrainExecutionProfile executionProfile,
            List<FullGraphExperimentPair> pairs) {
        String mechanisms = pairs.stream().map(pair -> pair.toJsonLine())
                .collect(java.util.stream.Collectors.joining(",\n"));
        return "{\n"
                + "  \"recordType\":\"m8TrustedResearchHostQualification\",\n"
                + "  \"schemaVersion\":\"M8-C1-C2-HOST-QUALIFICATION-V1\",\n"
                + "  \"status\":\"PASS\",\n"
                + "  \"qualificationOnly\":true,\n"
                + "  \"trainEvidence\":false,\n"
                + "  \"verifiedOutcomeLabelsProduced\":false,\n"
                + "  \"confirmatoryInferenceAllowed\":false,\n"
                + "  \"holdoutOpened\":false,\n"
                + "  \"transferOpened\":false,\n"
                + "  \"createdAt\":" + quote(Instant.now().toString()) + ",\n"
                + "  \"ciFreeze\":{\n"
                + "    \"gitCommit\":" + quote(context.gitCommit()) + ",\n"
                + "    \"ciWorkflowUrl\":" + quote(context.ciWorkflowUrl()) + ",\n"
                + "    \"ciRunId\":" + quote(context.ciRunId()) + ",\n"
                + "    \"candidateCodeFingerprint\":" + quote(context.candidateCodeFingerprint()) + ",\n"
                + "    \"dependencyManifestFingerprint\":" + quote(context.dependencyManifestFingerprint()) + ",\n"
                + "    \"ciAttestationSubject\":" + quote(GitCiM8FreezePrerequisiteVerifier.attestationSubject(context)) + ",\n"
                + "    \"ciAttestationRecordSha256\":" + quote(ciAttestationRecordFingerprint) + "\n"
                + "  },\n"
                + "  \"trustedIssuerIds\":[" + java.util.Arrays.stream(ISSUERS).map(M8SingleUnitHostQualificationCli::quote)
                        .collect(java.util.stream.Collectors.joining(",")) + "],\n"
                + "  \"pinnedIssuerKeyFingerprints\":{" + issuerKeyFingerprints.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> quote(entry.getKey()) + ":" + quote(entry.getValue()))
                        .collect(java.util.stream.Collectors.joining(",")) + "},\n"
                + "  \"executionProfile\":{"
                + "\"profileId\":" + quote(executionProfile.profileId()) + ","
                + "\"requestInterpreterId\":" + quote(executionProfile.requestInterpreterId()) + ","
                + "\"reasoningProfileId\":" + quote(executionProfile.reasoningProfileId()) + ","
                + "\"plannerProfileId\":" + quote(executionProfile.plannerProfileId()) + ","
                + "\"worldSimulatorProfileId\":" + quote(executionProfile.worldSimulatorProfileId()) + ","
                + "\"modelExecution\":" + quote(executionProfile.modelExecution().name()) + ","
                + "\"deterministicRequestInterpretation\":" + executionProfile.deterministicRequestInterpretation() + ","
                + "\"deterministicReasoning\":" + executionProfile.deterministicReasoning() + ","
                + "\"deterministicPlanning\":" + executionProfile.deterministicPlanning() + ","
                + "\"deterministicWorldSimulation\":" + executionProfile.deterministicWorldSimulation() + ","
                + "\"externalConnectorsDisabled\":" + executionProfile.externalConnectorsDisabled() + ","
                + "\"productionCredentialsAbsent\":" + executionProfile.productionCredentialsAbsent() + "},\n"
                + "  \"unit\":{\n"
                + "    \"split\":\"TRAIN\",\n"
                + "    \"purpose\":\"preflight-only; not admitted to the TRAIN telemetry or learning bundle\",\n"
                + "    \"experimentUnitId\":" + quote(unit.experimentUnitId()) + ",\n"
                + "    \"fixtureProfile\":" + quote(unit.fixtureProfile()) + ",\n"
                + "    \"scenarioFamily\":" + quote(unit.scenarioFamily()) + ",\n"
                + "    \"seed\":" + unit.seed() + ",\n"
                + "    \"fixtureFingerprint\":" + quote(unit.fixture().fixtureFingerprint()) + ",\n"
                + "    \"effectiveInputFingerprint\":" + quote(unit.effectiveInputFingerprint()) + "\n"
                + "  },\n"
                + "  \"matchedMechanismPairs\":[\n" + mechanisms + "\n  ]\n"
                + "}\n";
    }

    private static void writeNewAtomically(Path output, String contents) throws IOException {
        Path normalized = output.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null) throw new IllegalArgumentException("qualification report output needs a parent directory");
        Files.createDirectories(parent);
        if (Files.exists(normalized, java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw new IOException("refusing to overwrite qualification report: " + normalized);
        Path pending = Files.createTempFile(parent, normalized.getFileName().toString(), ".pending");
        boolean moved = false;
        try {
            Files.writeString(pending, contents, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try { Files.move(pending, normalized, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(pending, normalized); }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(pending);
        }
    }

    private static Map<String, String> parseArguments(String[] args) {
        if (args == null || args.length == 0 || args.length % 2 != 0)
            throw new IllegalArgumentException("expected paired --option value arguments; see M8-C1-C2 runbook");
        var result = new TreeMap<String, String>();
        for (int index = 0; index < args.length; index += 2) {
            String key = args[index];
            if (!key.startsWith("--") || key.length() < 3)
                throw new IllegalArgumentException("invalid command-line option: " + key);
            String name = key.substring(2);
            if (!Set.of("repo-root", "ci-freeze-context", "pinned-public-key-dir", "attestation-dir",
                    "training-archive-dir", "output").contains(name))
                throw new IllegalArgumentException("unknown command-line option: " + key);
            String value = Objects.requireNonNull(args[index + 1], "option value").trim();
            if (value.isEmpty() || result.putIfAbsent(name, value) != null)
                throw new IllegalArgumentException("empty or duplicate command-line option: " + key);
        }
        if (result.size() != 6) throw new IllegalArgumentException("all six command-line options are required");
        return Map.copyOf(result);
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing required option --" + key);
        return value;
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            switch (ch) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> { if (ch < 0x20) result.append(String.format("\\u%04x", (int) ch)); else result.append(ch); }
            }
        }
        return result.append('"').toString();
    }
}
