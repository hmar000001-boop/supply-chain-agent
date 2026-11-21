package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.actions.api.ActionEffect;
import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Versioned deterministic binary codec used to content-address immutable recovery artifacts. */
public final class RecoveryProcedureArtifactCodec {
    private static final int MAGIC = 0x42325052; // B2PR
    private static final int MAX_BYTES = 256 * 1024;
    private static final int MAX_ITEMS = 256;

    public byte[] encode(RecoveryProcedureArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeInt(artifact.formatVersion());
                writeString(out, artifact.sourceDraftId());
                writeString(out, artifact.scopeId().value());
                writeString(out, artifact.conditionKey());
                writeString(out, artifact.objectiveFingerprint());
                out.writeInt(artifact.actions().size());
                for (RegisteredRecoveryActionReference action : artifact.actions()) {
                    writeString(out, action.actionId());
                    writeString(out, action.requiredCapability().name());
                    writeString(out, action.effect().name());
                    writeString(out, action.descriptorFingerprint());
                }
                writeStrings(out, artifact.preconditionKeys());
                writeStrings(out, artifact.expectedPostconditionKeys());
                writeStrings(out, artifact.failureConditionKeys());
                out.writeInt(artifact.evidence().size());
                for (EvidenceReference evidence : artifact.evidence()) {
                    writeString(out, evidence.evidenceType());
                    writeString(out, evidence.evidenceId());
                }
                writeString(out, artifact.sourceFingerprint());
                writeString(out, artifact.approvedBy());
                writeString(out, artifact.approvalReference());
                writeInstant(out, artifact.approvedAt());
            }
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MAX_BYTES) throw new IllegalArgumentException("recovery artifact exceeds maximum encoded size");
            return encoded;
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory recovery artifact encoding failed", impossible);
        }
    }

    public RecoveryProcedureArtifact decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length == 0 || encoded.length > MAX_BYTES) throw new IllegalArgumentException("invalid recovery artifact size");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("invalid recovery artifact magic");
            int version = in.readInt();
            String draftId = readString(in);
            MemoryScopeId scope = new MemoryScopeId(readString(in));
            String condition = readString(in);
            String objective = readString(in);
            int actionCount = readCount(in);
            List<RegisteredRecoveryActionReference> actions = new ArrayList<>(actionCount);
            for (int i = 0; i < actionCount; i++) {
                actions.add(new RegisteredRecoveryActionReference(readString(in),
                        Capability.valueOf(readString(in)), ActionEffect.valueOf(readString(in)), readString(in)));
            }
            List<String> preconditions = readStrings(in);
            List<String> postconditions = readStrings(in);
            List<String> failures = readStrings(in);
            int evidenceCount = readCount(in);
            List<EvidenceReference> evidence = new ArrayList<>(evidenceCount);
            for (int i = 0; i < evidenceCount; i++) evidence.add(new EvidenceReference(readString(in), readString(in)));
            String sourceFingerprint = readString(in);
            String approvedBy = readString(in);
            String approvalReference = readString(in);
            Instant approvedAt = readInstant(in);
            if (in.read() != -1) throw new IllegalArgumentException("trailing bytes after recovery artifact");
            return new RecoveryProcedureArtifact(version, draftId, scope, condition, objective, actions,
                    preconditions, postconditions, failures, evidence, sourceFingerprint, approvedBy,
                    approvalReference, approvedAt);
        } catch (IOException | IllegalArgumentException ex) {
            if (ex instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalArgumentException("invalid recovery artifact encoding", ex);
        }
    }

    private static void writeStrings(DataOutputStream out, List<String> values) throws IOException {
        out.writeInt(values.size());
        for (String value : values) writeString(out, value);
    }

    private static List<String> readStrings(DataInputStream in) throws IOException {
        int count = readCount(in);
        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) values.add(readString(in));
        return List.copyOf(values);
    }

    private static int readCount(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_ITEMS) throw new IllegalArgumentException("recovery artifact item count is out of range");
        return count;
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] utf8 = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (utf8.length > 16 * 1024) throw new IllegalArgumentException("recovery artifact field is too large");
        out.writeInt(utf8.length);
        out.write(utf8);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 16 * 1024) throw new IllegalArgumentException("recovery artifact field length is out of range");
        byte[] value = in.readNBytes(length);
        if (value.length != length) throw new IllegalArgumentException("truncated recovery artifact field");
        return new String(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void writeInstant(DataOutputStream out, Instant value) throws IOException {
        out.writeLong(value.getEpochSecond());
        out.writeInt(value.getNano());
    }

    private static Instant readInstant(DataInputStream in) throws IOException {
        long seconds = in.readLong();
        int nanos = in.readInt();
        if (nanos < 0 || nanos > 999_999_999) throw new IllegalArgumentException("invalid artifact instant precision");
        return Instant.ofEpochSecond(seconds, nanos);
    }
}
