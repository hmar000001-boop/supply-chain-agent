package dev.supplychain.agent.planning.api;

import dev.supplychain.agent.domain.world.SupplyChainSnapshot;
import dev.supplychain.agent.memory.working.WorkingMemorySnapshot;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical, content-sensitive planning-input fingerprint used to protect resumable planner sessions.
 * Consumable resource ceilings and the resume-session identifier are intentionally excluded so a caller
 * can replenish computation. Structural bounds (depth and result cardinality) remain fingerprinted because
 * changing either alters the searched space or the result contract. All other inputs that can change search
 * meaning are included.
 */
final class PlanningRequestFingerprint {
    private PlanningRequestFingerprint() { }

    static String canonicalInput(PlanningRequest request) {
        Objects.requireNonNull(request, "request");
        var values = new ArrayList<Object>();
        values.add(request.executionId());
        values.add(request.objective());
        values.add(request.initialWorld());
        values.add(request.workingMemory());
        values.add(request.evidence());
        values.add(request.availableActionDefinitions());
        values.add(request.hardConstraints());
        values.add(request.scoringProfile());
        values.add(request.riskPolicy());
        values.add(request.authorityPolicy());
        values.add(request.complexity());
        // Depth and result cardinality change the search space/result contract; resource ceilings may be replenished on resume.
        values.add(request.budget().maxDepth());
        values.add(request.budget().maxCandidates());
        values.add(request.requestedAt());
        values.add(request.uncertaintyAssessmentReference());
        // A B4 method binding changes which strategy is authoritative, so it must not reuse
        // a planner session created under a different intention method/version.
        values.add(request.plannerSelectionOverride());
        return encode(values);
    }

    static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return token("string", text);
        if (value instanceof Character character) return token("char", character.toString());
        if (value instanceof BigDecimal decimal) return token("decimal", decimal.stripTrailingZeros().toPlainString());
        if (value instanceof BigInteger integer) return token("integer", integer.toString());
        if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            return token(value.getClass().getName(), value.toString());
        }
        if (value instanceof Currency currency) return token("currency", currency.getCurrencyCode());
        if (value instanceof TemporalAccessor || value instanceof Duration) {
            return token(value.getClass().getName(), value.toString());
        }
        if (value instanceof SupplyChainSnapshot snapshot) {
            return encodeSnapshot(snapshot);
        }
        if (value instanceof WorkingMemorySnapshot snapshot) {
            return encodeWorkingMemory(snapshot);
        }
        if (value instanceof Map<?, ?> map) {
            var entries = map.entrySet().stream()
                    .map(entry -> Map.entry(encode(entry.getKey()), encode(entry.getValue())))
                    .sorted(Map.Entry.comparingByKey())
                    .toList();
            return token("map", entries.toString());
        }
        if (value instanceof Set<?> set) {
            return token("set", set.stream().map(PlanningRequestFingerprint::encode).sorted().toList().toString());
        }
        if (value instanceof Collection<?> collection) {
            return token("list", collection.stream().map(PlanningRequestFingerprint::encode).toList().toString());
        }
        if (value.getClass().isArray()) {
            var elements = new ArrayList<String>();
            for (int i = 0; i < Array.getLength(value); i++) elements.add(encode(Array.get(value, i)));
            return token("array", elements.toString());
        }
        if (value.getClass().isRecord()) return encodeRecord(value);
        throw new IllegalArgumentException("Unsupported type in planning fingerprint: " + value.getClass().getName());
    }

    private static String encodeSnapshot(SupplyChainSnapshot snapshot) {
        return token("SupplyChainSnapshot", encode(java.util.List.of(
                snapshot.version(), snapshot.capturedAt(), snapshot.products(), snapshot.substitutions(),
                snapshot.suppliers(), snapshot.locations(), snapshot.inventoryLots(), snapshot.customerOrders(),
                snapshot.purchaseOrders(), snapshot.carriers(), snapshot.routes(), snapshot.shipments(),
                snapshot.reservations(), snapshot.disruptions(), snapshot.recoveryObjectives(),
                snapshot.recoveryCandidates())));
    }

    private static String encodeWorkingMemory(WorkingMemorySnapshot snapshot) {
        // WorkingMemoryId is a storage identity, not cognitive content. Exclude it so equivalent
        // immutable snapshots re-created from the same persisted content retain the same plan identity.
        return token("WorkingMemorySnapshot", encode(java.util.List.of(
                snapshot.scopeId(), snapshot.version(), snapshot.createdAt(), snapshot.updatedAt(),
                snapshot.expiresAt(), snapshot.values(), snapshot.hypotheses(), snapshot.sourceEvidence())));
    }

    private static String encodeRecord(Object record) {
        var parts = new ArrayList<String>();
        parts.add(record.getClass().getName());
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            try {
                parts.add(token(component.getName(), encode(component.getAccessor().invoke(record))));
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalStateException("Could not fingerprint record component " + component.getName(), exception);
            }
        }
        return token("record", parts.toString());
    }

    private static String token(String type, String value) {
        return type.length() + ":" + type + value.length() + ":" + value;
    }
}
