package dev.supplychain.agent.evaluation.experiment.research;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/** Read-only filesystem repository for versioned, signed attestation records. */
public final class FileSystemM8SignedAttestationRepository implements M8SignedAttestationRepository {
    private static final int MAX_ATTESTATION_BYTES = 32 * 1024;
    private final Path root;

    public FileSystemM8SignedAttestationRepository(Path root) throws IOException {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        if (Files.exists(this.root, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(this.root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(this.root)))
            throw new IOException("attestation repository root must be a real directory");
        Files.createDirectories(this.root);
        if (Files.isSymbolicLink(this.root)) throw new SecurityException("attestation repository root cannot be a symlink");
    }

    @Override public Optional<M8SignedAttestation> find(String evidenceType, String subjectFingerprint) throws IOException {
        String type = Objects.requireNonNull(evidenceType, "evidenceType");
        String subject = Objects.requireNonNull(subjectFingerprint, "subjectFingerprint");
        // Reuse constructor validation before deriving a path.
        M8SignedAttestation probe = new M8SignedAttestation(type, subject, "repository-probe", java.time.Instant.EPOCH,
                Base64.getEncoder().encodeToString(new byte[64]));
        Path file = root.resolve(probe.evidenceType() + "--" + probe.subjectFingerprint() + ".attestation").normalize();
        if (!file.getParent().equals(root)) throw new SecurityException("attestation lookup path escapes repository root");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new SecurityException("attestation record must be a regular, non-symlink file");
        long size = Files.size(file);
        if (size < 1 || size > MAX_ATTESTATION_BYTES) throw new SecurityException("attestation record size is invalid");
        return Optional.of(decode(Files.readAllBytes(file)));
    }

    /** Stable UTF-8 line codec; Base64 fields avoid delimiter ambiguity. */
    public static byte[] encode(M8SignedAttestation value) {
        Objects.requireNonNull(value, "value");
        String encoded = "M8-ATTESTATION-FILE-V1\n"
                + b64(value.evidenceType()) + "\n"
                + value.subjectFingerprint() + "\n"
                + b64(value.issuerId()) + "\n"
                + value.issuedAt() + "\n"
                + value.signatureBase64() + "\n";
        return encoded.getBytes(StandardCharsets.US_ASCII);
    }

    public static M8SignedAttestation decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        String[] lines = new String(bytes, StandardCharsets.US_ASCII).split("\\n", -1);
        if (lines.length != 7 || !"M8-ATTESTATION-FILE-V1".equals(lines[0]) || !lines[6].isEmpty())
            throw new SecurityException("attestation file has an invalid version or field count");
        try {
            return new M8SignedAttestation(unb64(lines[1]), lines[2], unb64(lines[3]),
                    java.time.Instant.parse(lines[4]), lines[5]);
        } catch (RuntimeException invalid) {
            throw new SecurityException("attestation file contains invalid fields", invalid);
        }
    }

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String unb64(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
