package dev.supplychain.agent.mechanisms.b6;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Governed calibration lifecycle; a score is never called calibrated until independently validated and activated. */
public final class UncertaintyCalibrationService {
    private final UncertaintyCalibrationRepository repository;
    private final UncertaintyCalibrationTrainer trainer;
    public UncertaintyCalibrationService(UncertaintyCalibrationRepository repository, UncertaintyCalibrationTrainer trainer) {
        this.repository=Objects.requireNonNull(repository,"repository"); this.trainer=Objects.requireNonNull(trainer,"trainer");
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
        UncertaintyCalibrationProfile profile=repository.findActive(
                new MemoryScopeId(raw.reference().memoryScopeId()),required(targetId)).orElse(null);
        return profile == null ? raw : raw.calibratedBy(profile);
    }
    private UncertaintyCalibrationProfile get(String id,long version) {
        var value=repository.findById(required(id)).orElseThrow(()->new IllegalArgumentException("unknown calibration profile: "+id));
        if(value.version()!=version) throw new IllegalStateException("calibration profile version conflict"); return value;
    }
    private static String required(String value){String s=Objects.requireNonNull(value,"value").trim();if(s.isEmpty())throw new IllegalArgumentException("value cannot be blank");return s;}
}
