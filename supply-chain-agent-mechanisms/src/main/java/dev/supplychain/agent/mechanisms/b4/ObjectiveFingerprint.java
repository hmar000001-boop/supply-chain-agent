package dev.supplychain.agent.mechanisms.b4;

import dev.supplychain.agent.domain.recovery.RecoveryObjective;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stable, unambiguous fingerprint of the immutable objective content.
 * Fields are UTF-8 length-prefixed; delimiters inside user-controlled text cannot change field boundaries.
 */
public record ObjectiveFingerprint(String value) {
    private static final String SCHEMA = "recovery-objective-fingerprint-v1";

    public ObjectiveFingerprint {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("fingerprint must be lowercase SHA-256 hex");
        }
    }

    public static ObjectiveFingerprint from(RecoveryObjective objective) {
        if (objective == null) throw new NullPointerException("objective");
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                writeField(output, SCHEMA);
                writeField(output, objective.id().value());
                writeField(output, objective.description());
                writeField(output, objective.targetFulfillmentAt().toString());

                var orders = objective.affectedOrders().stream().map(order -> order.value()).sorted().toList();
                output.writeInt(orders.size());
                for (String order : orders) writeField(output, order);

                var constraints = objective.hardConstraints().stream().map(Enum::name).sorted().toList();
                output.writeInt(constraints.size());
                for (String constraint : constraints) writeField(output, constraint);
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            return new ObjectiveFingerprint(HexFormat.of().formatHex(digest));
        } catch (IOException impossibleForByteArrayStream) {
            throw new IllegalStateException("Unable to encode objective fingerprint", impossibleForByteArrayStream);
        } catch (NoSuchAlgorithmException impossibleOnJava21) {
            throw new IllegalStateException("SHA-256 is unavailable", impossibleOnJava21);
        }
    }

    private static void writeField(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}
