package dev.supplychain.agent.evaluation.experiment.research;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Loads an exact allow-list of externally provisioned Ed25519 public keys. Key files are PEM
 * SubjectPublicKeyInfo (BEGIN PUBLIC KEY), named {@code <issuer-id>.pub}. No private-key format
 * is accepted, and symlink/non-regular entries are rejected.
 */
public final class M8PinnedEd25519PublicKeyLoader {
    private static final long MAX_KEY_FILE_BYTES = 16 * 1024;

    private M8PinnedEd25519PublicKeyLoader() { }

    public static Map<String, PublicKey> load(Path directory, Set<String> expectedIssuerIds) throws IOException {
        Path root = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        Set<String> expected = Set.copyOf(Objects.requireNonNull(expectedIssuerIds, "expectedIssuerIds"));
        if (expected.isEmpty()) throw new IllegalArgumentException("expected issuer allow-list cannot be empty");
        expected.forEach(M8PinnedEd25519PublicKeyLoader::requireIssuerId);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
            throw new IOException("pinned-key directory must exist and must not be a symlink: " + root);

        TreeSet<String> actualFileNames = new TreeSet<>();
        try (var entries = Files.list(root)) {
            for (Path entry : entries.toList()) {
                if (Files.isSymbolicLink(entry) || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS))
                    throw new SecurityException("pinned-key directory contains a symlink or non-regular entry: " + entry.getFileName());
                actualFileNames.add(entry.getFileName().toString());
            }
        }
        TreeSet<String> expectedFileNames = new TreeSet<>();
        expected.forEach(issuer -> expectedFileNames.add(issuer + ".pub"));
        if (!actualFileNames.equals(expectedFileNames))
            throw new SecurityException("pinned-key directory must contain exactly the configured issuer .pub files; expected="
                    + expectedFileNames + ", found=" + actualFileNames);

        Map<String, PublicKey> keys = new HashMap<>();
        KeyFactory factory;
        try { factory = KeyFactory.getInstance("Ed25519"); }
        catch (Exception unavailable) { throw new IllegalStateException("JDK does not provide Ed25519", unavailable); }
        for (String issuer : expected) {
            Path file = root.resolve(issuer + ".pub");
            long size = Files.size(file);
            if (size < 1 || size > MAX_KEY_FILE_BYTES)
                throw new SecurityException("pinned public-key file has an invalid size: " + issuer);
            String pem = Files.readString(file, StandardCharsets.US_ASCII).trim();
            String begin = "-----BEGIN PUBLIC KEY-----";
            String end = "-----END PUBLIC KEY-----";
            if (!pem.startsWith(begin) || !pem.endsWith(end)
                    || pem.indexOf(begin, begin.length()) >= 0
                    || pem.indexOf(end) != pem.length() - end.length()
                    || pem.contains("PRIVATE KEY") || pem.contains("CERTIFICATE"))
                throw new SecurityException("pinned key must contain exactly one public-key PEM block: " + issuer);
            String encoded = pem.substring(begin.length(), pem.length() - end.length()).replaceAll("\\s", "");
            byte[] der;
            try { der = Base64.getDecoder().decode(encoded); }
            catch (IllegalArgumentException invalid) { throw new SecurityException("invalid public-key PEM Base64: " + issuer, invalid); }
            try {
                PublicKey key = factory.generatePublic(new X509EncodedKeySpec(der));
                if (!"EdDSA".equalsIgnoreCase(key.getAlgorithm()) && !"Ed25519".equalsIgnoreCase(key.getAlgorithm()))
                    throw new SecurityException("pinned key is not Ed25519: " + issuer);
                keys.put(issuer, key);
            } catch (java.security.spec.InvalidKeySpecException invalid) {
                throw new SecurityException("invalid Ed25519 SubjectPublicKeyInfo for issuer: " + issuer, invalid);
            }
        }
        return Map.copyOf(keys);
    }

    private static void requireIssuerId(String issuer) {
        if (issuer == null || !issuer.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))
            throw new IllegalArgumentException("unsafe Ed25519 issuer ID in trust allow-list");
    }
}
