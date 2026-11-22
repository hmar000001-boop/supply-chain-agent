package dev.supplychain.agent.evaluation.experiment.research;

import dev.supplychain.agent.evaluation.experiment.mechanism.MechanismTrainingArtifactBundle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Versioned deterministic codec for the complete immutable TRAIN record graph. It uses a small
 * allow-listed binary value grammar rather than Java native object deserialization. Only record
 * types under dev.supplychain.agent.*, enums, primitive values, time values and collections are
 * accepted. Archive contents are bounded before allocation and the root type is fixed.
 */
public final class ReflectiveM8TrainingArtifactBundleCodec implements M8TrainingArtifactBundleCodec {
    private static final byte[] MAGIC = "M8-TRAIN-BUNDLE-RECORD-GRAPH".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 1;
    private static final int MAX_ARCHIVE_BYTES = 256 * 1024 * 1024;
    private static final int MAX_COLLECTION_SIZE = 2_000_000;
    private static final int MAX_DEPTH = 80;
    private static final int MAX_STRING_BYTES = 16 * 1024 * 1024;

    @Override public String codecId() { return "m8-record-graph-v1"; }

    @Override public byte[] encode(MechanismTrainingArtifactBundle bundle) throws Exception {
        Objects.requireNonNull(bundle, "bundle");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC.length); out.write(MAGIC); out.writeInt(VERSION);
            writeValue(out, bundle, 0);
        }
        byte[] result = bytes.toByteArray();
        if (result.length > MAX_ARCHIVE_BYTES) throw new IllegalArgumentException("TRAIN bundle archive exceeds maximum size");
        return result;
    }

    @Override public MechanismTrainingArtifactBundle decode(byte[] archiveBytes) throws Exception {
        Objects.requireNonNull(archiveBytes, "archiveBytes");
        if (archiveBytes.length == 0 || archiveBytes.length > MAX_ARCHIVE_BYTES)
            throw new SecurityException("TRAIN bundle archive size is invalid");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(archiveBytes))) {
            int magicLength = in.readInt();
            if (magicLength != MAGIC.length) throw new SecurityException("TRAIN bundle archive magic length mismatch");
            byte[] actualMagic = in.readNBytes(magicLength);
            if (!java.util.Arrays.equals(actualMagic, MAGIC)) throw new SecurityException("TRAIN bundle archive magic mismatch");
            if (in.readInt() != VERSION) throw new SecurityException("unsupported TRAIN bundle codec version");
            Object decoded = readValue(in, 0);
            if (in.available() != 0) throw new SecurityException("TRAIN bundle archive contains trailing bytes");
            if (!(decoded instanceof MechanismTrainingArtifactBundle bundle))
                throw new SecurityException("TRAIN bundle archive root type is not MechanismTrainingArtifactBundle");
            return bundle;
        } catch (SecurityException invalid) {
            throw invalid;
        } catch (Exception malformed) {
            throw new SecurityException("TRAIN bundle archive cannot be decoded safely", malformed);
        }
    }

    private static void writeValue(DataOutputStream out, Object value, int depth) throws Exception {
        guardDepth(depth);
        if (value == null) { out.writeByte(0); return; }
        if (value instanceof String string) { out.writeByte(1); writeString(out, string); return; }
        if (value instanceof Integer number) { out.writeByte(2); out.writeInt(number); return; }
        if (value instanceof Long number) { out.writeByte(3); out.writeLong(number); return; }
        if (value instanceof Double number) { out.writeByte(4); out.writeLong(Double.doubleToLongBits(number)); return; }
        if (value instanceof Boolean bool) { out.writeByte(5); out.writeBoolean(bool); return; }
        if (value instanceof Float number) { out.writeByte(6); out.writeInt(Float.floatToIntBits(number)); return; }
        if (value instanceof Short number) { out.writeByte(7); out.writeShort(number); return; }
        if (value instanceof Byte number) { out.writeByte(8); out.writeByte(number); return; }
        if (value instanceof BigDecimal decimal) { out.writeByte(9); writeString(out, decimal.toPlainString()); return; }
        if (value instanceof BigInteger integer) { out.writeByte(10); writeString(out, integer.toString()); return; }
        if (value instanceof Instant instant) { out.writeByte(11); out.writeLong(instant.getEpochSecond()); out.writeInt(instant.getNano()); return; }
        if (value instanceof Duration duration) { out.writeByte(12); out.writeLong(duration.getSeconds()); out.writeInt(duration.getNano()); return; }
        if (value instanceof Enum<?> enumeration) { out.writeByte(13); writeString(out, enumeration.getDeclaringClass().getName()); writeString(out, enumeration.name()); return; }
        if (value instanceof Optional<?> optional) { out.writeByte(14); out.writeBoolean(optional.isPresent()); if (optional.isPresent()) writeValue(out, optional.get(), depth + 1); return; }
        if (value instanceof List<?> list) {
            out.writeByte(15); writeSize(out, list.size()); for (Object item : list) writeValue(out, item, depth + 1); return;
        }
        if (value instanceof Set<?> set) {
            out.writeByte(16); List<byte[]> items = set.stream().map(ReflectiveM8TrainingArtifactBundleCodec::encodedKey).sorted(UNSIGNED_BYTES).toList();
            writeSize(out, items.size()); for (byte[] item : items) out.write(item); return;
        }
        if (value instanceof Map<?, ?> map) {
            out.writeByte(17);
            List<MapEntryBytes> entries = new ArrayList<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) entries.add(new MapEntryBytes(encodedKey(entry.getKey()), encodedKey(entry.getValue())));
            entries.sort(Comparator.comparing(MapEntryBytes::key, UNSIGNED_BYTES));
            writeSize(out, entries.size()); for (MapEntryBytes entry : entries) { out.write(entry.key()); out.write(entry.value()); }
            return;
        }
        if (value.getClass().isArray()) {
            out.writeByte(18); Class<?> component = value.getClass().getComponentType(); writeString(out, component.getName());
            int length = Array.getLength(value); writeSize(out, length);
            for (int index = 0; index < length; index++) writeValue(out, Array.get(value, index), depth + 1);
            return;
        }
        Class<?> type = value.getClass();
        if (!type.isRecord() || !type.getName().startsWith("dev.supplychain.agent."))
            throw new SecurityException("unsupported object in TRAIN record graph: " + type.getName());
        out.writeByte(19); writeString(out, type.getName());
        RecordComponent[] components = type.getRecordComponents();
        writeSize(out, components.length);
        for (RecordComponent component : components) {
            writeString(out, component.getName());
            var accessor = component.getAccessor(); accessor.setAccessible(true);
            writeValue(out, accessor.invoke(value), depth + 1);
        }
    }

    private static Object readValue(DataInputStream in, int depth) throws Exception {
        guardDepth(depth);
        return switch (in.readUnsignedByte()) {
            case 0 -> null;
            case 1 -> readString(in);
            case 2 -> in.readInt();
            case 3 -> in.readLong();
            case 4 -> Double.longBitsToDouble(in.readLong());
            case 5 -> in.readBoolean();
            case 6 -> Float.intBitsToFloat(in.readInt());
            case 7 -> in.readShort();
            case 8 -> in.readByte();
            case 9 -> new BigDecimal(readString(in));
            case 10 -> new BigInteger(readString(in));
            case 11 -> Instant.ofEpochSecond(in.readLong(), in.readInt());
            case 12 -> Duration.ofSeconds(in.readLong(), in.readInt());
            case 13 -> readEnum(in);
            case 14 -> in.readBoolean() ? Optional.ofNullable(readValue(in, depth + 1)) : Optional.empty();
            case 15 -> readList(in, depth);
            case 16 -> readSet(in, depth);
            case 17 -> readMap(in, depth);
            case 18 -> readArray(in, depth);
            case 19 -> readRecord(in, depth);
            default -> throw new SecurityException("unknown TRAIN record-graph value tag");
        };
    }

    private static Object readEnum(DataInputStream in) throws Exception {
        Class<?> type = loadAllowed(readString(in));
        if (!type.isEnum()) throw new SecurityException("encoded enum class is not an enum");
        @SuppressWarnings({"rawtypes", "unchecked"}) Object result = Enum.valueOf((Class<? extends Enum>) type, readString(in));
        return result;
    }
    private static List<Object> readList(DataInputStream in, int depth) throws Exception {
        int size = readSize(in); List<Object> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) values.add(readValue(in, depth + 1));
        return values;
    }
    private static Set<Object> readSet(DataInputStream in, int depth) throws Exception {
        int size = readSize(in); Set<Object> values = new LinkedHashSet<>();
        for (int i = 0; i < size; i++) if (!values.add(readValue(in, depth + 1)))
            throw new SecurityException("TRAIN record graph contains duplicate set elements");
        return values;
    }
    private static Map<Object, Object> readMap(DataInputStream in, int depth) throws Exception {
        int size = readSize(in); Map<Object, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            Object key = readValue(in, depth + 1); Object value = readValue(in, depth + 1);
            if (values.containsKey(key)) throw new SecurityException("TRAIN record graph contains duplicate map keys");
            values.put(key, value);
        }
        return values;
    }
    private static Object readArray(DataInputStream in, int depth) throws Exception {
        Class<?> component = primitiveOrAllowed(readString(in)); int length = readSize(in);
        Object array = Array.newInstance(component, length);
        for (int i = 0; i < length; i++) Array.set(array, i, readValue(in, depth + 1));
        return array;
    }
    private static Object readRecord(DataInputStream in, int depth) throws Exception {
        Class<?> type = loadAllowed(readString(in));
        if (!type.isRecord()) throw new SecurityException("encoded record class is not a record");
        RecordComponent[] components = type.getRecordComponents();
        int count = readSize(in);
        if (count != components.length) throw new SecurityException("record component count does not match current schema: " + type.getName());
        Class<?>[] parameterTypes = new Class<?>[count]; Object[] values = new Object[count];
        for (int i = 0; i < count; i++) {
            String name = readString(in);
            if (!components[i].getName().equals(name)) throw new SecurityException("record component order/name mismatch: " + type.getName());
            parameterTypes[i] = components[i].getType(); values[i] = readValue(in, depth + 1);
        }
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes); constructor.setAccessible(true);
        try { return constructor.newInstance(values); }
        catch (java.lang.reflect.InvocationTargetException invalid) {
            Throwable cause = invalid.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw invalid;
        }
    }

    private static Class<?> primitiveOrAllowed(String name) throws ClassNotFoundException {
        return switch (name) {
            case "boolean" -> boolean.class; case "byte" -> byte.class; case "short" -> short.class;
            case "int" -> int.class; case "long" -> long.class; case "float" -> float.class;
            case "double" -> double.class; case "char" -> char.class;
            default -> loadAllowed(name);
        };
    }
    private static Class<?> loadAllowed(String name) throws ClassNotFoundException {
        boolean allowed = name.startsWith("dev.supplychain.agent.evaluation.experiment.mechanism.")
                || name.startsWith("dev.supplychain.agent.evaluation.experiment.scenario.")
                || name.startsWith("dev.supplychain.agent.mechanisms.")
                || name.startsWith("dev.supplychain.agent.memory.")
                || name.startsWith("dev.supplychain.agent.domain.")
                || name.startsWith("dev.supplychain.agent.actions.api.")
                || name.startsWith("dev.supplychain.agent.planning.api.")
                || name.startsWith("dev.supplychain.agent.core.");
        if (!allowed) throw new SecurityException("record graph class is outside the explicit M8 data-model allow-list: " + name);
        return Class.forName(name, false, Thread.currentThread().getContextClassLoader());
    }
    private static byte[] encodedKey(Object value) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) { writeValue(out, value, 1); }
            return bytes.toByteArray();
        } catch (Exception invalid) { throw new IllegalArgumentException("cannot deterministically encode collection element", invalid); }
    }
    private record MapEntryBytes(byte[] key, byte[] value) { }
    private static final Comparator<byte[]> UNSIGNED_BYTES = (left, right) -> {
        int length = Math.min(left.length, right.length);
        for (int i = 0; i < length; i++) { int result = Integer.compare(left[i] & 0xff, right[i] & 0xff); if (result != 0) return result; }
        return Integer.compare(left.length, right.length);
    };
    private static void writeString(DataOutputStream out, String value) throws Exception {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IllegalArgumentException("record-graph string exceeds size limit");
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static String readString(DataInputStream in) throws Exception {
        int size = in.readInt(); if (size < 0 || size > MAX_STRING_BYTES) throw new SecurityException("record-graph string size is invalid");
        byte[] bytes = in.readNBytes(size); if (bytes.length != size) throw new java.io.EOFException("truncated record-graph string");
        return new String(bytes, StandardCharsets.UTF_8);
    }
    private static void writeSize(DataOutputStream out, int size) throws Exception {
        if (size < 0 || size > MAX_COLLECTION_SIZE) throw new IllegalArgumentException("record-graph collection size is invalid");
        out.writeInt(size);
    }
    private static int readSize(DataInputStream in) throws Exception {
        int size = in.readInt(); if (size < 0 || size > MAX_COLLECTION_SIZE) throw new SecurityException("record-graph collection size is invalid");
        return size;
    }
    private static void guardDepth(int depth) {
        if (depth > MAX_DEPTH) throw new SecurityException("TRAIN record graph exceeds maximum nesting depth");
    }
}
