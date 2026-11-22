package dev.supplychain.agent.evaluation.experiment.research;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** SHA-256 helpers and deterministic source-tree/dependency-manifest fingerprints. */
public final class M8CandidateCodeFingerprint {
    private M8CandidateCodeFingerprint() { }

    public static String sha256(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    public static String sha256(String value) {
        return sha256(Objects.requireNonNull(value, "value").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Computes the canonical implementation fingerprint shared with
     * scripts/fingerprint-m8-experiment-candidate.py. Documentation and generated research
     * manifests are deliberately excluded, preventing self-referential hashes and ensuring that
     * the fingerprint is an identity for executable/configuration inputs rather than its report.
     */
    public static String compute(Path repositoryRoot) throws IOException {
        Path root = Objects.requireNonNull(repositoryRoot, "repositoryRoot").toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
            throw new IOException("candidate root must be a real directory");
        List<Path> files;
        try (var walk = Files.walk(root)) {
            files = walk.filter(path -> !path.equals(root))
                    .filter(path -> included(root.relativize(path)))
                    .filter(path -> {
                        if (Files.isSymbolicLink(path))
                            throw new SecurityException("candidate tree contains a symbolic link: " + path);
                        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
                    })
                    .sorted(java.util.Comparator.comparing(path -> root.relativize(path).toString().replace('\\', '/')))
                    .toList();
        }
        var digest = newDigest();
        for (Path file : files) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            String fileSha256 = sha256(Files.readAllBytes(file));
            digest.update(relative.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(fileSha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            digest.update((byte) '\n');
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static boolean included(Path relative) {
        for (Path part : relative) {
            String name = part.toString();
            if (Set.of(".git", "target", "build", ".gradle", ".idea", ".vscode", "out",
                    "node_modules", "__pycache__").contains(name)) return false;
        }
        if (relative.getFileName().toString().endsWith(".pyc")) return false;
        String first = relative.getName(0).toString();
        if (first.startsWith("supply-chain-agent-")) return true;
        if (Set.of(".mvn", ".github", "scripts").contains(first)) return true;
        if (relative.getNameCount() == 1) return Set.of("pom.xml", "mvnw", "mvnw.cmd",
                ".gitignore", ".gitattributes", ".env.example").contains(first);
        return false;
    }

    public static String dependencyManifestFingerprint(Path repositoryRoot) throws IOException {
        Path root = Objects.requireNonNull(repositoryRoot, "repositoryRoot").toAbsolutePath().normalize();
        List<Path> poms;
        try (var walk = Files.walk(root)) {
            poms = walk.filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .filter(path -> !excluded(root.relativize(path)))
                    .filter(path -> {
                        if (Files.isSymbolicLink(path))
                            throw new SecurityException("dependency manifest contains a symbolic link: " + path);
                        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
                    })
                    .sorted(java.util.Comparator.comparing(path -> root.relativize(path).toString().replace('\\', '/')))
                    .toList();
        }
        var digest = newDigest();
        for (Path pom : poms) {
            update(digest, root.relativize(pom).toString().replace('\\', '/').getBytes(java.nio.charset.StandardCharsets.UTF_8));
            update(digest, Files.readAllBytes(pom));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static boolean excluded(Path relative) {
        for (Path part : relative) {
            String name = part.toString();
            if (name.equals(".git") || name.equals("target") || name.equals("build") || name.equals(".gradle")
                    || name.equals(".idea") || name.equals("out") || name.equals("node_modules")) return true;
        }
        return false;
    }
    private static MessageDigest newDigest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private static void update(MessageDigest digest, byte[] bytes) {
        digest.update(java.nio.ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());
        digest.update(bytes);
    }
}
