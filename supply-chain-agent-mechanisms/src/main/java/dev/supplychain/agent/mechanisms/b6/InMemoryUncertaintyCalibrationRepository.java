package dev.supplychain.agent.mechanisms.b6;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test/reference repository; production hosts should use the PostgreSQL adapter. */
public final class InMemoryUncertaintyCalibrationRepository implements UncertaintyCalibrationRepository {
    private final ConcurrentHashMap<String, UncertaintyCalibrationProfile> profiles = new ConcurrentHashMap<>();
    @Override public void saveNew(UncertaintyCalibrationProfile profile) {
        if (profiles.putIfAbsent(profile.id(), profile) != null) throw new IllegalStateException("calibration profile already exists");
    }
    @Override public Optional<UncertaintyCalibrationProfile> findById(String id) { return Optional.ofNullable(profiles.get(id)); }
    @Override public Optional<UncertaintyCalibrationProfile> findActive(MemoryScopeId scope, String target) {
        return profiles.values().stream().filter(p -> p.scopeId().equals(scope) && p.targetId().equals(target)
                && p.status() == UncertaintyCalibrationProfile.Status.ACTIVE)
                .max(Comparator.comparingLong(UncertaintyCalibrationProfile::version));
    }
    @Override public boolean compareAndSet(String id, long expected, UncertaintyCalibrationProfile replacement) {
        final boolean[] updated = {false};
        profiles.compute(id, (key,current) -> { if(current != null && current.version() == expected) { updated[0]=true; return replacement; } return current; });
        return updated[0];
    }
    @Override public synchronized boolean activateReplacingActive(String id, long expectedVersion,
            UncertaintyCalibrationProfile replacement) {
        UncertaintyCalibrationProfile current = profiles.get(id);
        if (current == null || current.version() != expectedVersion
                || current.status() != UncertaintyCalibrationProfile.Status.VALIDATED
                || !current.id().equals(replacement.id()) || !current.scopeId().equals(replacement.scopeId())
                || !current.targetId().equals(replacement.targetId())
                || replacement.version() != expectedVersion + 1
                || replacement.status() != UncertaintyCalibrationProfile.Status.ACTIVE) return false;
        for (var entry : List.copyOf(profiles.entrySet())) {
            UncertaintyCalibrationProfile existing = entry.getValue();
            if (!existing.id().equals(id) && existing.scopeId().equals(replacement.scopeId())
                    && existing.targetId().equals(replacement.targetId())
                    && existing.status() == UncertaintyCalibrationProfile.Status.ACTIVE) {
                profiles.put(existing.id(), existing.retire(replacement.activatedAt()));
            }
        }
        profiles.put(id, replacement);
        return true;
    }
    @Override public List<UncertaintyCalibrationProfile> history(MemoryScopeId scope, String target, int maximum) {
        if(maximum<1||maximum>10000) throw new IllegalArgumentException("maximum must be in 1..10000");
        return profiles.values().stream().filter(p->p.scopeId().equals(scope)&&p.targetId().equals(target))
                .sorted(Comparator.comparing(UncertaintyCalibrationProfile::createdAt).thenComparing(UncertaintyCalibrationProfile::id))
                .limit(maximum).toList();
    }
}
