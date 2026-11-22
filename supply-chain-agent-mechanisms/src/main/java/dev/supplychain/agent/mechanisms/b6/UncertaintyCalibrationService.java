package dev.supplychain.agent.mechanisms.b6;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Governed calibration lifecycle; a score is never called calibrated until independently validated and activated. */
public final class UncertaintyCalibrationService {
    private final UncertaintyCalibrationRepository repository;
    private final UncertaintyCalibrationTrainer trainer;
    private final UncertaintyCalibrationProfile shadowProfile;
    private final Map<String, ShadowCalibrationTrace> shadowTraces = new ConcurrentHashMap<>();
    public UncertaintyCalibrationService(UncertaintyCalibrationRepository repository, UncertaintyCalibrationTrainer trainer) {
        this(repository, trainer, null);
    }
    /** Research-only constructor. Shadow profiles must remain TRAINED and are never persisted/activated here. */
    public UncertaintyCalibrationService(UncertaintyCalibrationRepository repository,
            UncertaintyCalibrationTrainer trainer, UncertaintyCalibrationProfile shadowProfile) {
        this.repository=Objects.requireNonNull(repository,"repository");
        this.trainer=Objects.requireNonNull(trainer,"trainer");
        this.shadowProfile=shadowProfile;
        if (shadowProfile != null && shadowProfile.status() != UncertaintyCalibrationProfile.Status.TRAINED)
            throw new SecurityException("B6 experiment shadow profile must be TRAINED, never VALIDATED or ACTIVE");
    }
    public Optional<UncertaintyCalibrationProfile> shadowProfile() { return Optional.ofNullable(shadowProfile); }

    /** Exact raw/transformed pair retained only by this disposable research service for independent audit. */
    public Optional<ShadowCalibrationTrace> shadowTrace(UncertaintyAssessment.Reference reference) {
        Objects.requireNonNull(reference, "reference");
        return Optional.ofNullable(shadowTraces.get(traceKey(reference)));
    }

    public record ShadowCalibrationTrace(UncertaintyAssessment raw, UncertaintyAssessment transformed,
                                         String profileId, long profileVersion, String targetId) {
        public ShadowCalibrationTrace {
            raw = Objects.requireNonNull(raw, "raw");
            transformed = Objects.requireNonNull(transformed, "transformed");
            profileId = required(profileId);
            targetId = required(targetId);
            if (profileVersion < 1 || !raw.reference().equals(transformed.reference())
                    || transformed.calibration().status() != UncertaintyAssessment.CalibrationStatus.NOT_CALIBRATED)
                throw new SecurityException("invalid B6 shadow trace or operational calibration status");
        }
    }
    public UncertaintyCalibrationProfile train(MemoryScopeId scope, String targetId,
            List<UncertaintyCalibrationObservation> training, String trainerId, Instant at) {
        var profile=trainer.train(scope,targetId,training,trainerId,at); repository.saveNew(profile); return profile;
    }
    public UncertaintyCalibrationProfile validate(String profileId, long expectedVersion,
            List<UncertaintyCalibrationObservation> holdout, String reviewerId) {
        var current=get(profileId,expectedVersion);
        var validated=trainer.validate(current,holdout,reviewerId).profile();
        if(!repository.compareAndSet(profileId,expectedVersion,validated)) throw new IllegalStateException("calibration profile changed concurrently");
        return validated;
    }
    public UncertaintyCalibrationProfile activate(String profileId,long expectedVersion,String approverId,Instant at) {
        var current=get(profileId,expectedVersion);
        var active=current.activate(approverId,at);
        if(!repository.activateReplacingActive(profileId,expectedVersion,active))
            throw new IllegalStateException("calibration profile or active target changed concurrently; activation was not applied");
        return active;
    }
    public Optional<UncertaintyCalibrationProfile> active(MemoryScopeId scope,String targetId) {
        return repository.findActive(Objects.requireNonNull(scope,"scope"),required(targetId));
    }
    public boolean isActiveVersion(String profileId, long version, MemoryScopeId scope, String targetId) {
        return repository.findById(required(profileId)).filter(profile -> profile.version() == version
                && profile.status() == UncertaintyCalibrationProfile.Status.ACTIVE
                && profile.scopeId().equals(scope) && profile.targetId().equals(required(targetId))).isPresent();
    }

    public UncertaintyAssessment calibrate(UncertaintyAssessment raw,String targetId) {
        Objects.requireNonNull(raw,"raw");
        MemoryScopeId scope = new MemoryScopeId(raw.reference().memoryScopeId());
        UncertaintyCalibrationProfile profile=repository.findActive(scope,required(targetId)).orElse(null);
        if (profile != null) return raw.calibratedBy(profile);
        if (shadowProfile != null && shadowProfile.scopeId().equals(scope)
                && shadowProfile.targetId().equals(required(targetId))) {
            UncertaintyAssessment transformed = raw.shadowCalibratedBy(shadowProfile);
            shadowTraces.put(traceKey(raw.reference()), new ShadowCalibrationTrace(raw, transformed,
                    shadowProfile.id(), shadowProfile.version(), shadowProfile.targetId()));
            return transformed;
        }
        return raw;
    }
    private UncertaintyCalibrationProfile get(String id,long version) {
        var value=repository.findById(required(id)).orElseThrow(()->new IllegalArgumentException("unknown calibration profile: "+id));
        if(value.version()!=version) throw new IllegalStateException("calibration profile version conflict"); return value;
    }
    private static String traceKey(UncertaintyAssessment.Reference reference) {
        return reference.executionId() + "|" + reference.assessmentId() + "|" + reference.version()
                + "|" + reference.memoryScopeId();
    }
    private static String required(String value){String s=Objects.requireNonNull(value,"value").trim();if(s.isEmpty())throw new IllegalArgumentException("value cannot be blank");return s;}
}
