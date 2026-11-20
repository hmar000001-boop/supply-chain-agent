package dev.supplychain.agent.mechanisms.b6;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, scoped uncertainty assessment. The graph carries only {@link Reference}; the
 * authoritative assessment and its provenance remain in the host-owned assessment store.
 * A value in [0,1] is not a calibrated probability unless calibration evidence is attached.
 */
public record UncertaintyAssessment(Reference reference, Instant assessedAt, Instant validUntil,
                                    String provenanceReference, Calibration calibration,
                                    Map<UncertaintyDimension, UncertaintySignal> signals) {
    public UncertaintyAssessment {
        reference = Objects.requireNonNull(reference, "reference");
        assessedAt = Objects.requireNonNull(assessedAt, "assessedAt");
        validUntil = Objects.requireNonNull(validUntil, "validUntil");
        if (!validUntil.isAfter(assessedAt)) {
            throw new IllegalArgumentException("uncertainty assessment validity must end after assessment time");
        }
        provenanceReference = required(provenanceReference, "provenanceReference");
        calibration = Objects.requireNonNull(calibration, "calibration");
        Objects.requireNonNull(signals, "signals");
        var copy = new EnumMap<UncertaintyDimension, UncertaintySignal>(UncertaintyDimension.class);
        copy.putAll(signals);
        for (var dimension : UncertaintyDimension.values()) {
            var signal = copy.get(dimension);
            if (signal == null) {
                throw new IllegalArgumentException("missing uncertainty assessment for " + dimension);
            }
            if (signal.dimension() != dimension) {
                throw new IllegalArgumentException("uncertainty map key does not match signal dimension");
            }
        }
        if (copy.size() != UncertaintyDimension.values().length) {
            throw new IllegalArgumentException("uncertainty assessment contains an unsupported dimension");
        }
        signals = Map.copyOf(copy);
    }

    public double maximumUncertainty() {
        return signals.values().stream().mapToDouble(UncertaintySignal::uncertainty).max().orElseThrow();
    }

    public boolean contains(UncertaintyDimension dimension) {
        return signals.containsKey(dimension);
    }

    public boolean isCurrentFor(String executionId, String memoryScopeId, Instant now) {
        Objects.requireNonNull(now, "now");
        return reference.executionId().equals(executionId)
                && reference.memoryScopeId().equals(memoryScopeId)
                && !now.isBefore(assessedAt)
                && now.isBefore(validUntil);
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }

    /** Small checkpoint-safe identity carried between graph nodes. */
    public record Reference(String assessmentId, long version, String executionId, String memoryScopeId) {
        public Reference {
            assessmentId = required(assessmentId, "assessmentId");
            executionId = required(executionId, "executionId");
            memoryScopeId = required(memoryScopeId, "memoryScopeId");
            if (version < 1) throw new IllegalArgumentException("uncertainty assessment version must be positive");
        }
    }

    public record Calibration(CalibrationStatus status, String profileId,
                              String validationEvidenceReference, long validationSampleCount) {
        public Calibration {
            status = Objects.requireNonNull(status, "status");
            profileId = optional(profileId);
            validationEvidenceReference = optional(validationEvidenceReference);
            if (validationSampleCount < 0) throw new IllegalArgumentException("validationSampleCount cannot be negative");
            if (status == CalibrationStatus.CALIBRATED
                    && (profileId.isEmpty() || validationEvidenceReference.isEmpty() || validationSampleCount < 1)) {
                throw new IllegalArgumentException("calibrated uncertainty requires a profile, validation evidence and positive sample count");
            }
            if (status == CalibrationStatus.NOT_CALIBRATED
                    && (!profileId.isEmpty() || !validationEvidenceReference.isEmpty() || validationSampleCount != 0)) {
                throw new IllegalArgumentException("uncalibrated uncertainty cannot claim calibration evidence");
            }
        }

        public static Calibration notCalibrated() {
            return new Calibration(CalibrationStatus.NOT_CALIBRATED, "", "", 0);
        }

        private static String optional(String value) { return value == null ? "" : value.trim(); }
    }

    public enum CalibrationStatus { NOT_CALIBRATED, CALIBRATED }
}
