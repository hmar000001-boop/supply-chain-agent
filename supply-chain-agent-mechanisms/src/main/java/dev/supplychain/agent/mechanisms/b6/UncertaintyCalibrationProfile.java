package dev.supplychain.agent.mechanisms.b6;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Target- and scope-bound calibration model with independent train/validation provenance. */
public record UncertaintyCalibrationProfile(
        String id, MemoryScopeId scopeId, String targetId, long version,
        Map<UncertaintyDimension, List<UncertaintyCalibrationBin>> bins,
        String trainingDatasetFingerprint, String validationDatasetFingerprint,
        long trainingSampleCount, long validationSampleCount, double validationExpectedCalibrationError,
        String trainedBy, String reviewedBy, String activatedBy,
        Status status, Instant createdAt, Instant validatedAt, Instant activatedAt,
        Set<String> trainingSampleIdHashes, Set<String> trainingEvidenceHashes,
        Set<String> validationSampleIdHashes, Set<String> validationEvidenceHashes) {
    public UncertaintyCalibrationProfile {
        id = required(id, "id"); scopeId = Objects.requireNonNull(scopeId, "scopeId");
        targetId = required(targetId, "targetId");
        if (version < 1 || trainingSampleCount < 1 || validationSampleCount < 0)
            throw new IllegalArgumentException("invalid calibration profile counters");
        var copy = new EnumMap<UncertaintyDimension, List<UncertaintyCalibrationBin>>(UncertaintyDimension.class);
        Objects.requireNonNull(bins, "bins").forEach((dimension, values) -> {
            List<UncertaintyCalibrationBin> stable = List.copyOf(values);
            if (stable.size() != 10) throw new IllegalArgumentException("each calibration dimension requires ten fixed bins");
            for (int i = 0; i < 10; i++) if (stable.get(i).index() != i)
                throw new IllegalArgumentException("calibration bins must be contiguous and ordered");
            copy.put(Objects.requireNonNull(dimension, "dimension"), stable);
        });
        if (copy.size() != UncertaintyDimension.values().length)
            throw new IllegalArgumentException("calibration profile must contain every uncertainty dimension");
        bins = Map.copyOf(copy);
        trainingSampleIdHashes = immutableHashes(trainingSampleIdHashes, "trainingSampleIdHashes");
        trainingEvidenceHashes = immutableHashes(trainingEvidenceHashes, "trainingEvidenceHashes");
        validationSampleIdHashes = immutableHashes(validationSampleIdHashes, "validationSampleIdHashes");
        validationEvidenceHashes = immutableHashes(validationEvidenceHashes, "validationEvidenceHashes");
        if (trainingSampleIdHashes.size() != trainingSampleCount || trainingEvidenceHashes.size() != trainingSampleCount)
            throw new IllegalArgumentException("training provenance hashes must identify every unique training observation");
        if (!disjoint(trainingSampleIdHashes, validationSampleIdHashes)
                || !disjoint(trainingEvidenceHashes, validationEvidenceHashes))
            throw new SecurityException("training and validation must not reuse sample IDs or labelled evidence records");
        trainingDatasetFingerprint = sha256(trainingDatasetFingerprint, "trainingDatasetFingerprint");
        validationDatasetFingerprint = validationDatasetFingerprint == null ? "" : validationDatasetFingerprint.trim();
        if (!validationDatasetFingerprint.isEmpty() && !validationDatasetFingerprint.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("validationDatasetFingerprint must be empty or SHA-256");
        if (!validationDatasetFingerprint.isEmpty() && validationDatasetFingerprint.equals(trainingDatasetFingerprint))
            throw new SecurityException("calibration and validation datasets must be independent");
        if (!Double.isFinite(validationExpectedCalibrationError) || validationExpectedCalibrationError < 0
                || validationExpectedCalibrationError > 1)
            throw new IllegalArgumentException("validation ECE must be in [0,1]");
        trainedBy = required(trainedBy, "trainedBy");
        reviewedBy = optional(reviewedBy); activatedBy = optional(activatedBy);
        status = Objects.requireNonNull(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (status == Status.TRAINED && (reviewedBy != null || validatedAt != null || activatedBy != null || activatedAt != null
                || !validationSampleIdHashes.isEmpty() || !validationEvidenceHashes.isEmpty()))
            throw new IllegalArgumentException("trained profile cannot carry review, validation or activation data");
        if ((status == Status.VALIDATED || status == Status.ACTIVE || status == Status.RETIRED)
                && (reviewedBy == null || validatedAt == null || validationDatasetFingerprint.isEmpty() || validationSampleCount < 1
                    || validationSampleIdHashes.size() != validationSampleCount || validationEvidenceHashes.size() != validationSampleCount))
            throw new IllegalArgumentException("validated profile requires independent validation provenance and reviewer");
        if (status == Status.ACTIVE && (activatedBy == null || activatedAt == null))
            throw new IllegalArgumentException("active profile requires approver and activation time");
        if (status == Status.RETIRED && activatedAt == null)
            throw new IllegalArgumentException("retired profile must have been activated");
    }

    public double calibrate(UncertaintyDimension dimension, double predicted) {
        if (!Double.isFinite(predicted) || predicted < 0 || predicted > 1)
            throw new IllegalArgumentException("predicted uncertainty must be in [0,1]");
        int index = Math.min(9, (int) Math.floor(predicted * 10.0));
        UncertaintyCalibrationBin bin = bins.get(Objects.requireNonNull(dimension, "dimension")).get(index);
        return bin.sampleCount() == 0 ? predicted : bin.calibratedUncertainty();
    }

    public UncertaintyCalibrationProfile validated(String reviewer, String validationFingerprint,
            long validationCount, double ece, Instant at, Set<String> validationSampleHashes, Set<String> validationEvidenceHashes) {
        if (status != Status.TRAINED) throw new IllegalStateException("only a trained calibration profile can be validated");
        String actor = required(reviewer, "reviewer");
        if (trainedBy.equals(actor)) throw new SecurityException("calibration trainer cannot independently validate its profile");
        return new UncertaintyCalibrationProfile(id, scopeId, targetId, version + 1, bins,
                trainingDatasetFingerprint, validationFingerprint, trainingSampleCount, validationCount, ece,
                trainedBy, actor, null, Status.VALIDATED, createdAt, Objects.requireNonNull(at, "at"), null,
                trainingSampleIdHashes, trainingEvidenceHashes, validationSampleHashes, validationEvidenceHashes);
    }

    public UncertaintyCalibrationProfile activate(String approver, Instant at) {
        if (status != Status.VALIDATED) throw new IllegalStateException("only a validated calibration profile can be activated");
        String actor = required(approver, "approver");
        Instant activationTime = Objects.requireNonNull(at, "at");
        if (activationTime.isBefore(validatedAt)) throw new IllegalArgumentException("calibration cannot activate before independent validation");
        if (actor.equals(trainedBy) || actor.equals(reviewedBy))
            throw new SecurityException("calibration activation requires an independent approver");
        return new UncertaintyCalibrationProfile(id, scopeId, targetId, version + 1, bins,
                trainingDatasetFingerprint, validationDatasetFingerprint, trainingSampleCount, validationSampleCount,
                validationExpectedCalibrationError, trainedBy, reviewedBy, actor, Status.ACTIVE,
                createdAt, validatedAt, activationTime, trainingSampleIdHashes, trainingEvidenceHashes,
                validationSampleIdHashes, validationEvidenceHashes);
    }

    public UncertaintyCalibrationProfile retire(Instant at) {
        if (status != Status.ACTIVE) throw new IllegalStateException("only an active calibration profile can be retired");
        Instant retirementTime = Objects.requireNonNull(at, "at");
        if (retirementTime.isBefore(activatedAt)) throw new IllegalArgumentException("calibration cannot retire before activation");
        return new UncertaintyCalibrationProfile(id, scopeId, targetId, version + 1, bins,
                trainingDatasetFingerprint, validationDatasetFingerprint, trainingSampleCount, validationSampleCount,
                validationExpectedCalibrationError, trainedBy, reviewedBy, activatedBy, Status.RETIRED,
                createdAt, validatedAt, retirementTime, trainingSampleIdHashes, trainingEvidenceHashes,
                validationSampleIdHashes, validationEvidenceHashes);
    }

    public enum Status { TRAINED, VALIDATED, ACTIVE, RETIRED }
    private static Set<String> immutableHashes(Set<String> values, String name) {
        Objects.requireNonNull(values, name);
        TreeSet<String> sorted = new TreeSet<>();
        for (String value : values) {
            String hash = required(value, name + " entry");
            if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(name + " entries must be SHA-256 hex");
            if (!sorted.add(hash)) throw new IllegalArgumentException(name + " contains duplicate hashes");
        }
        return Set.copyOf(sorted);
    }
    private static boolean disjoint(Set<String> left, Set<String> right) {
        return left.stream().noneMatch(right::contains);
    }
    private static String sha256(String value, String name) {
        String result = required(value, name);
        if (!result.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(name + " must be lowercase SHA-256 hex");
        return result;
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
