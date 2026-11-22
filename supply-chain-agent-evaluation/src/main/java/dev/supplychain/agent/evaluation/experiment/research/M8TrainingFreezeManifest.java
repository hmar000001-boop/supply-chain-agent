package dev.supplychain.agent.evaluation.experiment.research;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.evaluation.experiment.scenario.M8ExperimentScenarioFactory;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable manifest for a fully re-verified TRAIN bundle. Creating it never opens HOLDOUT. */
public record M8TrainingFreezeManifest(
        String protocolId,
        String generatorId,
        String candidateCodeFingerprint,
        String gitCommit,
        String ciWorkflowUrl,
        String ciRunId,
        String ciConclusion,
        boolean gitWorkingTreeClean,
        String dependencyManifestFingerprint,
        String freezePrerequisiteVerifierId,
        String trainAuditFingerprint,
        String trainingSafetyFingerprint,
        String trainingSafetyVerifierId,
        Map<ExperimentMechanism, M8ExperimentArtifactIntegrityAuditor.ArtifactRecord> trainArtifacts,
        M8TrainingArtifactArchive.StoredArtifact storedBundle,
        String bundleFingerprint,
        String bundleVerifierId,
        Instant frozenAt,
        boolean holdoutAccessOpened,
        boolean transferAccessOpened,
        String manifestFingerprint) {

    public M8TrainingFreezeManifest {
        protocolId = required(protocolId, "protocolId");
        generatorId = required(generatorId, "generatorId");
        if (!M8ExperimentScenarioFactory.PROTOCOL_ID.equals(protocolId)
                || !M8ExperimentScenarioFactory.GENERATOR_ID.equals(generatorId))
            throw new SecurityException("TRAIN freeze manifest belongs to a different experiment protocol/generator");
        candidateCodeFingerprint = sha(candidateCodeFingerprint, "candidateCodeFingerprint");
        gitCommit = required(gitCommit, "gitCommit");
        if (!gitCommit.matches("[0-9a-f]{40}|[0-9a-f]{64}"))
            throw new IllegalArgumentException("gitCommit must be a lowercase SHA-1 or SHA-256 commit ID");
        ciWorkflowUrl = required(ciWorkflowUrl, "ciWorkflowUrl");
        if (!ciWorkflowUrl.matches("https://[^\\s]+"))
            throw new IllegalArgumentException("ciWorkflowUrl must be an HTTPS URL");
        ciRunId = required(ciRunId, "ciRunId");
        if (!ciRunId.matches("[0-9]{1,32}")) throw new IllegalArgumentException("ciRunId must be numeric");
        ciConclusion = required(ciConclusion, "ciConclusion");
        if (!"SUCCESS".equals(ciConclusion) || !gitWorkingTreeClean)
            throw new SecurityException("TRAIN freeze manifest must bind a successful hosted CI run and clean Git tree");
        dependencyManifestFingerprint = sha(dependencyManifestFingerprint, "dependencyManifestFingerprint");
        freezePrerequisiteVerifierId = required(freezePrerequisiteVerifierId, "freezePrerequisiteVerifierId");
        trainAuditFingerprint = sha(trainAuditFingerprint, "trainAuditFingerprint");
        trainingSafetyFingerprint = sha(trainingSafetyFingerprint, "trainingSafetyFingerprint");
        trainingSafetyVerifierId = required(trainingSafetyVerifierId, "trainingSafetyVerifierId");
        var copy = new EnumMap<ExperimentMechanism, M8ExperimentArtifactIntegrityAuditor.ArtifactRecord>(ExperimentMechanism.class);
        copy.putAll(Objects.requireNonNull(trainArtifacts, "trainArtifacts"));
        if (!copy.keySet().equals(java.util.Set.of(ExperimentMechanism.values())))
            throw new SecurityException("TRAIN freeze requires all five exact mechanism telemetry artifacts");
        for (var entry : copy.entrySet()) {
            var artifact = entry.getValue();
            if (artifact.mechanism() != entry.getKey()
                    || artifact.split() != dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit.TRAIN
                    || artifact.pairCount() != 280
                    || !"FULL_GRAPH_TELEMETRY_NO_VERIFIED_OUTCOME_LABELS".equals(artifact.evidenceKind()))
                throw new SecurityException("TRAIN artifact manifest contains foreign or incorrectly typed evidence");
        }
        trainArtifacts = Map.copyOf(copy);
        storedBundle = Objects.requireNonNull(storedBundle, "storedBundle");
        bundleFingerprint = sha(bundleFingerprint, "bundleFingerprint");
        if (!storedBundle.bundleFingerprint().equals(bundleFingerprint))
            throw new SecurityException("stored bundle reference and bundle fingerprint differ");
        bundleVerifierId = required(bundleVerifierId, "bundleVerifierId");
        frozenAt = Objects.requireNonNull(frozenAt, "frozenAt");
        if (holdoutAccessOpened || transferAccessOpened)
            throw new SecurityException("training freeze must not open HOLDOUT or TRANSFER access");
        String expected = calculate(protocolId, generatorId, candidateCodeFingerprint, gitCommit, ciWorkflowUrl,
                ciRunId, ciConclusion, gitWorkingTreeClean, dependencyManifestFingerprint,
                freezePrerequisiteVerifierId, trainAuditFingerprint, trainingSafetyFingerprint,
                trainingSafetyVerifierId, trainArtifacts, storedBundle, bundleFingerprint, bundleVerifierId, frozenAt, holdoutAccessOpened, transferAccessOpened);
        manifestFingerprint = sha(manifestFingerprint, "manifestFingerprint");
        if (!manifestFingerprint.equals(expected))
            throw new SecurityException("TRAIN freeze manifest fingerprint does not reproduce");
    }

    public static M8TrainingFreezeManifest create(String candidateCodeFingerprint, String gitCommit,
            String ciWorkflowUrl, String ciRunId, String ciConclusion, boolean gitWorkingTreeClean,
            String dependencyManifestFingerprint, String freezePrerequisiteVerifierId,
            M8ExperimentSafetyEvidence trainingSafety, M8ExperimentArtifactIntegrityAuditor.AuditResult audit,
            M8TrainingArtifactArchive.StoredArtifact storedBundle, String bundleVerifierId, Instant frozenAt) {
        Objects.requireNonNull(audit, "audit");
        Objects.requireNonNull(trainingSafety, "trainingSafety");
        if (trainingSafety.split() != dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit.TRAIN
                || trainingSafety.episodeCount() != 280)
            throw new SecurityException("freeze manifest requires exact TRAIN safety evidence");
        trainingSafety.requireSafe();
        String fingerprint = calculate(audit.protocolId(), audit.generatorId(), candidateCodeFingerprint,
                gitCommit, ciWorkflowUrl, ciRunId, ciConclusion, gitWorkingTreeClean,
                dependencyManifestFingerprint, freezePrerequisiteVerifierId, audit.auditFingerprint(),
                trainingSafety.evidenceFingerprint(), trainingSafety.verifierId(), audit.artifacts(), storedBundle, storedBundle.bundleFingerprint(), bundleVerifierId,
                frozenAt, false, false);
        return new M8TrainingFreezeManifest(audit.protocolId(), audit.generatorId(), candidateCodeFingerprint,
                gitCommit, ciWorkflowUrl, ciRunId, ciConclusion, gitWorkingTreeClean,
                dependencyManifestFingerprint, freezePrerequisiteVerifierId, audit.auditFingerprint(),
                trainingSafety.evidenceFingerprint(), trainingSafety.verifierId(), audit.artifacts(), storedBundle, storedBundle.bundleFingerprint(), bundleVerifierId,
                frozenAt, false, false, fingerprint);
    }

    private static String calculate(String protocol, String generator, String candidate, String commit,
            String workflow, String runId, String ciConclusion, boolean gitClean,
            String dependencies, String freezeVerifier, String audit,
            String safetyFingerprint, String safetyVerifier,
            Map<ExperimentMechanism, M8ExperimentArtifactIntegrityAuditor.ArtifactRecord> artifacts,
            M8TrainingArtifactArchive.StoredArtifact bundle, String bundleFingerprint, String verifier,
            Instant frozenAt, boolean holdoutOpened, boolean transferOpened) {
        String artifactCanonical = artifacts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "|" + entry.getValue().path() + "|"
                        + entry.getValue().pairCount() + "|" + entry.getValue().unitSetFingerprint() + "|"
                        + entry.getValue().artifactSha256() + "|" + entry.getValue().pairRecordsSha256()
                        + "|" + entry.getValue().byteCount() + "|" + entry.getValue().evidenceKind())
                .collect(java.util.stream.Collectors.joining("\n"));
        String canonical = protocol + "|" + generator + "|" + candidate + "|" + commit + "|" + workflow
                + "|" + runId + "|" + ciConclusion + "|" + gitClean + "|" + dependencies + "|"
                + freezeVerifier + "|" + audit + "|" + safetyFingerprint + "|" + safetyVerifier
                + "|" + artifactCanonical + "|"
                + bundle.archiveName() + "|" + bundle.contentSha256() + "|" + bundle.bundleFingerprint()
                + "|" + bundle.codecId() + "|" + verifier + "|" + frozenAt + "|"
                + holdoutOpened + "|" + transferOpened;
        return M8VerifiedEndpointEvidence.sha256(canonical);
    }

    public String toJson() {
        StringBuilder json = new StringBuilder(4096);
        json.append('{').append("\"recordType\":\"m8TrainingFreezeManifest\"")
                .append(",\"protocolId\":").append(q(protocolId))
                .append(",\"generatorId\":").append(q(generatorId))
                .append(",\"candidateCodeFingerprint\":").append(q(candidateCodeFingerprint))
                .append(",\"gitCommit\":").append(q(gitCommit))
                .append(",\"ciWorkflowUrl\":").append(q(ciWorkflowUrl))
                .append(",\"ciRunId\":").append(q(ciRunId))
                .append(",\"ciConclusion\":").append(q(ciConclusion))
                .append(",\"gitWorkingTreeClean\":true")
                .append(",\"dependencyManifestFingerprint\":").append(q(dependencyManifestFingerprint))
                .append(",\"freezePrerequisiteVerifierId\":").append(q(freezePrerequisiteVerifierId))
                .append(",\"trainAuditFingerprint\":").append(q(trainAuditFingerprint))
                .append(",\"trainingSafetyFingerprint\":").append(q(trainingSafetyFingerprint))
                .append(",\"trainingSafetyVerifierId\":").append(q(trainingSafetyVerifierId))
                .append(",\"bundleFingerprint\":").append(q(bundleFingerprint))
                .append(",\"bundleVerifierId\":").append(q(bundleVerifierId))
                .append(",\"bundleArchiveName\":").append(q(storedBundle.archiveName()))
                .append(",\"bundleContentSha256\":").append(q(storedBundle.contentSha256()))
                .append(",\"bundleByteCount\":").append(storedBundle.byteCount())
                .append(",\"bundleCodecId\":").append(q(storedBundle.codecId()))
                .append(",\"frozenAt\":").append(q(frozenAt.toString()))
                .append(",\"holdoutAccessOpened\":false,\"transferAccessOpened\":false")
                .append(",\"manifestFingerprint\":").append(q(manifestFingerprint))
                .append(",\"trainArtifacts\":[");
        boolean first = true;
        for (ExperimentMechanism mechanism : ExperimentMechanism.values()) {
            var artifact = trainArtifacts.get(mechanism);
            if (!first) json.append(',');
            first = false;
            json.append('{').append("\"mechanism\":").append(q(mechanism.name()))
                    .append(",\"path\":").append(q(artifact.path()))
                    .append(",\"pairCount\":").append(artifact.pairCount())
                    .append(",\"byteCount\":").append(artifact.byteCount())
                    .append(",\"unitSetFingerprint\":").append(q(artifact.unitSetFingerprint()))
                    .append(",\"artifactSha256\":").append(q(artifact.artifactSha256()))
                    .append(",\"pairRecordsSha256\":").append(q(artifact.pairRecordsSha256()))
                    .append(",\"evidenceKind\":").append(q(artifact.evidenceKind())).append('}');
        }
        return json.append("]}\n").toString();
    }

    private static String q(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }

    static String sha(String value, String name) {
        String result = required(value, name);
        if (!result.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        return result;
    }
    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
