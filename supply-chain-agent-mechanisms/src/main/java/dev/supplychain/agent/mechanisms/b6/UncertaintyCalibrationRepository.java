package dev.supplychain.agent.mechanisms.b6;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.util.List;
import java.util.Optional;

public interface UncertaintyCalibrationRepository {
    void saveNew(UncertaintyCalibrationProfile profile);
    Optional<UncertaintyCalibrationProfile> findById(String id);
    Optional<UncertaintyCalibrationProfile> findActive(MemoryScopeId scope, String targetId);
    boolean compareAndSet(String id, long expectedVersion, UncertaintyCalibrationProfile replacement);
    /** Atomically retires the prior active target profile and activates this validated profile. */
    boolean activateReplacingActive(String id, long expectedVersion, UncertaintyCalibrationProfile replacement);
    List<UncertaintyCalibrationProfile> history(MemoryScopeId scope, String targetId, int maximum);
}
