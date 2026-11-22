package dev.supplychain.agent.evaluation.experiment.research;

import dev.supplychain.agent.evaluation.experiment.mechanism.MechanismTrainingArtifactBundle;
import dev.supplychain.agent.evaluation.experiment.mechanism.MechanismTrainingArtifactVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;

/**
 * Freeze gate for the verified TRAIN artifacts. It requires a successful hosted CI receipt,
 * clean source tree, five independently audited telemetry artifacts, a complete typed training
 * bundle, an independent bundle verifier, and a content-addressed archive read-back.
 * It intentionally has no operation that opens HOLDOUT or TRANSFER.
 */
public final class M8TrainingArtifactFreezeService {
    private final M8ExperimentArtifactIntegrityAuditor auditor;
    private final MechanismTrainingArtifactVerifier bundleVerifier;
    private final M8TrainingArtifactArchive archive;
    private final M8FreezePrerequisiteVerifier prerequisiteVerifier;
    private final M8ExperimentSafetyEvidenceVerifier safetyVerifier;

    public M8TrainingArtifactFreezeService(M8ExperimentArtifactIntegrityAuditor auditor,
            MechanismTrainingArtifactVerifier bundleVerifier, M8TrainingArtifactArchive archive,
            M8FreezePrerequisiteVerifier prerequisiteVerifier, M8ExperimentSafetyEvidenceVerifier safetyVerifier) {
        this.auditor = Objects.requireNonNull(auditor, "auditor");
        this.bundleVerifier = Objects.requireNonNull(bundleVerifier, "bundleVerifier");
        this.archive = Objects.requireNonNull(archive, "archive");
        this.prerequisiteVerifier = Objects.requireNonNull(prerequisiteVerifier, "prerequisiteVerifier");
        this.safetyVerifier = Objects.requireNonNull(safetyVerifier, "safetyVerifier");
        if (prerequisiteVerifier.verifierId() == null || prerequisiteVerifier.verifierId().isBlank())
            throw new IllegalArgumentException("freeze prerequisite verifier must have a stable identity");
        if (safetyVerifier.verifierId() == null || safetyVerifier.verifierId().isBlank())
            throw new IllegalArgumentException("TRAIN safety verifier must have a stable identity");
    }

    public M8TrainingFreezeManifest freeze(M8ExperimentArtifactIntegrityAuditor.AuditResult trainAudit,
            MechanismTrainingArtifactBundle bundle, M8ExperimentSafetyEvidence trainSafety,
            FreezeContext context, Path manifestOutput) throws Exception {
        Objects.requireNonNull(trainAudit, "trainAudit");
        Objects.requireNonNull(bundle, "bundle");
        Objects.requireNonNull(trainSafety, "trainSafety");
        Objects.requireNonNull(context, "context");
        Path output = Objects.requireNonNull(manifestOutput, "manifestOutput").toAbsolutePath().normalize();
        if (Files.exists(output)) throw new IOException("refusing to overwrite an existing TRAIN freeze manifest: " + output);
        if (!context.hostedCiGreen() || !"SUCCESS".equals(context.ciConclusion()))
            throw new SecurityException("TRAIN freeze requires an explicitly successful hosted CI run");
        prerequisiteVerifier.verify(context);
        if ("UNIDENTIFIED_HOST_VERIFIER".equals(bundleVerifier.verifierId())
                || !bundleVerifier.verifierId().equals(context.bundleVerifierId()))
            throw new SecurityException("TRAIN freeze requires a stable verifier identity matching the executing verifier instance");
        if (!context.gitWorkingTreeClean())
            throw new SecurityException("TRAIN freeze requires a clean Git working tree");
        if (trainSafety.split() != dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit.TRAIN
                || trainSafety.episodeCount() != 280
                || !trainSafety.verifierId().equals(safetyVerifier.verifierId()))
            throw new SecurityException("TRAIN safety evidence split, episode count or verifier identity mismatch");
        safetyVerifier.verify(trainSafety);
        trainSafety.requireSafe();
        if (!trainAudit.candidateCodeFingerprint().equals(context.candidateCodeFingerprint())
                || !bundle.candidateCodeFingerprint().equals(context.candidateCodeFingerprint()))
            throw new SecurityException("source candidate, telemetry audit and typed training bundle fingerprints differ");
        if (!bundle.protocolId().equals(trainAudit.protocolId())
                || !bundle.trainUnitFingerprints().equals(MechanismTrainingArtifactBundle.exactTrainUnitFingerprints()))
            throw new SecurityException("typed bundle is not bound to the exact locked TRAIN protocol/unit set");

        auditor.revalidate(trainAudit);
        bundleVerifier.verify(bundle);
        M8TrainingArtifactArchive.StoredArtifact stored = archive.store(bundle);
        MechanismTrainingArtifactBundle readBack = archive.load(stored);
        if (!readBack.artifactFingerprint().equals(bundle.artifactFingerprint())
                || !readBack.equals(bundle))
            throw new SecurityException("TRAIN archive read-back differs from the exact typed bundle");
        bundleVerifier.verify(readBack);
        auditor.revalidate(trainAudit);

        M8TrainingFreezeManifest manifest = M8TrainingFreezeManifest.create(
                context.candidateCodeFingerprint(), context.gitCommit(), context.ciWorkflowUrl(),
                context.ciRunId(), context.ciConclusion(), context.gitWorkingTreeClean(),
                context.dependencyManifestFingerprint(), prerequisiteVerifier.verifierId(),
                trainSafety, trainAudit, stored, context.bundleVerifierId(), Instant.now());
        writeNewAtomically(output, manifest.toJson());
        return manifest;
    }

    private static void writeNewAtomically(Path output, String content) throws IOException {
        Path parent = output.getParent();
        if (parent == null) throw new IOException("freeze manifest path must have a parent directory");
        Files.createDirectories(parent);
        if (Files.exists(output)) throw new IOException("refusing to overwrite an existing freeze manifest: " + output);
        Path pending = Files.createTempFile(parent, output.getFileName().toString(), ".pending");
        boolean moved = false;
        try {
            Files.writeString(pending, content, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try { Files.move(pending, output, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(pending, output);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(pending);
        }
    }

    public record FreezeContext(String candidateCodeFingerprint, String gitCommit,
            String ciWorkflowUrl, String ciRunId, String ciConclusion, boolean hostedCiGreen,
            boolean gitWorkingTreeClean, String dependencyManifestFingerprint, String bundleVerifierId) {
        public FreezeContext {
            candidateCodeFingerprint = M8TrainingFreezeManifest.sha(candidateCodeFingerprint, "candidateCodeFingerprint");
            gitCommit = Objects.requireNonNull(gitCommit, "gitCommit").trim();
            ciWorkflowUrl = Objects.requireNonNull(ciWorkflowUrl, "ciWorkflowUrl").trim();
            ciRunId = Objects.requireNonNull(ciRunId, "ciRunId").trim();
            ciConclusion = Objects.requireNonNull(ciConclusion, "ciConclusion").trim();
            dependencyManifestFingerprint = M8TrainingFreezeManifest.sha(dependencyManifestFingerprint, "dependencyManifestFingerprint");
            bundleVerifierId = Objects.requireNonNull(bundleVerifierId, "bundleVerifierId").trim();
            if (gitCommit.isEmpty() || ciWorkflowUrl.isEmpty() || ciRunId.isEmpty() || bundleVerifierId.isEmpty())
                throw new IllegalArgumentException("freeze context identity fields cannot be blank");
        }
    }
}
