package dev.supplychain.agent.evaluation.experiment.research;

import dev.supplychain.agent.evaluation.experiment.mechanism.MechanismTrainingArtifactBundle;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Content-addressed file archive with codec-based read-back; never overwrites different bundle bytes. */
public final class FileSystemM8TrainingArtifactArchive implements M8TrainingArtifactArchive {
    private final Path root;
    private final M8TrainingArtifactBundleCodec codec;

    public FileSystemM8TrainingArtifactArchive(Path root, M8TrainingArtifactBundleCodec codec) throws IOException {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.codec = Objects.requireNonNull(codec, "codec");
        if (codec.codecId() == null || !codec.codecId().matches("[A-Za-z0-9._-]{1,64}"))
            throw new IllegalArgumentException("bundle codec ID must be a safe versioned token");
        Files.createDirectories(this.root);
        if (!Files.isDirectory(this.root)) throw new IOException("training archive root is not a directory");
    }

    @Override
    public StoredArtifact store(MechanismTrainingArtifactBundle bundle) throws Exception {
        Objects.requireNonNull(bundle, "bundle");
        byte[] bytes = Objects.requireNonNull(codec.encode(bundle), "bundle codec returned null bytes");
        if (bytes.length == 0) throw new IOException("training bundle codec returned an empty archive");
        String archiveName = bundle.artifactFingerprint() + ".m8bundle";
        Path target = resolve(archiveName);
        String byteHash = M8ExperimentArtifactIntegrityAuditor.sha256(bytes);
        if (Files.exists(target)) {
            String existingHash = M8ExperimentArtifactIntegrityAuditor.sha256(Files.readAllBytes(target));
            if (!existingHash.equals(byteHash))
                throw new SecurityException("content-addressed TRAIN archive already exists with different bytes");
        } else {
            Path temporary = Files.createTempFile(root, archiveName, ".pending");
            boolean moved = false;
            try {
                Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target); }
                moved = true;
            } finally {
                if (!moved) Files.deleteIfExists(temporary);
            }
        }
        StoredArtifact stored = new StoredArtifact(archiveName, byteHash, bundle.artifactFingerprint(),
                bytes.length, codec.codecId());
        MechanismTrainingArtifactBundle readBack = load(stored);
        if (!readBack.artifactFingerprint().equals(bundle.artifactFingerprint()))
            throw new SecurityException("TRAIN archive read-back fingerprint differs from the in-memory bundle");
        return stored;
    }

    @Override
    public MechanismTrainingArtifactBundle load(StoredArtifact stored) throws Exception {
        Objects.requireNonNull(stored, "stored");
        if (!stored.codecId().equals(codec.codecId()))
            throw new SecurityException("TRAIN archive codec identity/version mismatch");
        Path path = resolve(stored.archiveName());
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length != stored.byteCount()
                || !M8ExperimentArtifactIntegrityAuditor.sha256(bytes).equals(stored.contentSha256()))
            throw new SecurityException("TRAIN archive bytes differ from their frozen content digest");
        MechanismTrainingArtifactBundle bundle = Objects.requireNonNull(codec.decode(bytes), "bundle codec decoded null");
        if (!bundle.artifactFingerprint().equals(stored.bundleFingerprint()))
            throw new SecurityException("decoded TRAIN bundle fingerprint differs from its archive reference");
        return bundle;
    }

    private Path resolve(String archiveName) {
        if (archiveName == null || !archiveName.matches("[0-9a-f]{64}\\.m8bundle"))
            throw new SecurityException("unsafe TRAIN archive name");
        Path resolved = root.resolve(archiveName).normalize();
        if (!resolved.startsWith(root)) throw new SecurityException("TRAIN archive path escapes its configured root");
        return resolved;
    }
}
