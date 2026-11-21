package dev.supplychain.agent.mechanisms.b6;

import dev.supplychain.agent.memory.common.MemoryScopeId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Deterministic ten-bin empirical reliability calibrator; it does not infer labels from model output. */
public final class UncertaintyCalibrationTrainer {
    public static final int MINIMUM_TRAINING_SAMPLES_PER_DIMENSION = 20;
    public static final int MINIMUM_VALIDATION_SAMPLES_PER_DIMENSION = 20;
    public static final double MAXIMUM_VALIDATION_ECE = 0.15;

    public UncertaintyCalibrationProfile train(MemoryScopeId scope, String targetId,
            List<UncertaintyCalibrationObservation> observations, String trainer, Instant at) {
        List<UncertaintyCalibrationObservation> samples = checked(scope, targetId, observations);
        ensureIndependentSampleIds(samples);
        var byDimension = group(samples);
        for (UncertaintyDimension dimension : UncertaintyDimension.values())
            if (byDimension.get(dimension).size() < MINIMUM_TRAINING_SAMPLES_PER_DIMENSION)
                throw new IllegalArgumentException("insufficient training labels for " + dimension + "; minimum is "
                        + MINIMUM_TRAINING_SAMPLES_PER_DIMENSION);
        var bins = new EnumMap<UncertaintyDimension, List<UncertaintyCalibrationBin>>(UncertaintyDimension.class);
        byDimension.forEach((dimension, values) -> bins.put(dimension, buildBins(values)));
        return new UncertaintyCalibrationProfile(UUID.randomUUID().toString(), scope, targetId, 1, bins,
                fingerprint(samples), "", samples.size(), 0, 0.0, trainer, null, null,
                UncertaintyCalibrationProfile.Status.TRAINED, at, null, null,
                sampleIdHashes(samples), evidenceHashes(samples), java.util.Set.of(), java.util.Set.of());
    }

    public ValidationResult validate(UncertaintyCalibrationProfile profile,
            List<UncertaintyCalibrationObservation> holdout, String reviewer) {
        Objects.requireNonNull(profile, "profile");
        List<UncertaintyCalibrationObservation> samples = checked(profile.scopeId(), profile.targetId(), holdout);
        ensureIndependentSampleIds(samples);
        String fingerprint = fingerprint(samples);
        java.util.Set<String> holdoutSampleHashes = sampleIdHashes(samples);
        java.util.Set<String> holdoutEvidenceHashes = evidenceHashes(samples);
        if (!java.util.Collections.disjoint(profile.trainingSampleIdHashes(), holdoutSampleHashes))
            throw new SecurityException("calibration holdout reuses a training sample ID");
        if (!java.util.Collections.disjoint(profile.trainingEvidenceHashes(), holdoutEvidenceHashes))
            throw new SecurityException("calibration holdout reuses labelled training evidence");
        if (profile.trainingDatasetFingerprint().equals(fingerprint))
            throw new SecurityException("calibration validation dataset must be independent from training");
        for (UncertaintyDimension dimension : UncertaintyDimension.values()) {
            long count = samples.stream().filter(sample -> sample.dimension() == dimension).count();
            if (count < MINIMUM_VALIDATION_SAMPLES_PER_DIMENSION)
                throw new IllegalArgumentException("insufficient independent validation labels for " + dimension);
        }
        double ece = expectedCalibrationError(profile, samples);
        var updated = profile.validated(reviewer, fingerprint, samples.size(), ece, samples.stream()
                .map(UncertaintyCalibrationObservation::observedAt).max(Comparator.naturalOrder()).orElseThrow(),
                holdoutSampleHashes, holdoutEvidenceHashes);
        if (ece > MAXIMUM_VALIDATION_ECE)
            throw new IllegalArgumentException("calibration profile failed independent validation: ECE=" + ece);
        return new ValidationResult(updated, ece, samples.size(), fingerprint);
    }

    public double expectedCalibrationError(UncertaintyCalibrationProfile profile,
            List<UncertaintyCalibrationObservation> observations) {
        var values = checked(profile.scopeId(), profile.targetId(), observations);
        double weightedError = 0.0;
        long total = 0;
        for (UncertaintyDimension dimension : UncertaintyDimension.values()) {
            List<UncertaintyCalibrationObservation> subset = values.stream()
                    .filter(value -> value.dimension() == dimension).toList();
            int[] count = new int[10]; double[] predicted = new double[10]; long[] errors = new long[10];
            for (var sample : subset) {
                int index = bin(sample.predictedUncertainty()); count[index]++;
                predicted[index] += sample.predictedUncertainty(); if (sample.realizedError()) errors[index]++;
            }
            for (int i = 0; i < 10; i++) if (count[i] > 0) {
                double observed = (double) errors[i] / count[i];
                double calibrated = profile.calibrate(dimension, predicted[i] / count[i]);
                weightedError += ((double) count[i] / values.size()) * Math.abs(observed - calibrated);
            }
            total += subset.size();
        }
        return total == 0 ? 1.0 : weightedError;
    }

    private static List<UncertaintyCalibrationObservation> checked(MemoryScopeId scope, String target,
            List<UncertaintyCalibrationObservation> observations) {
        Objects.requireNonNull(scope, "scope");
        if (target == null || target.isBlank()) throw new IllegalArgumentException("targetId cannot be blank");
        List<UncertaintyCalibrationObservation> samples = List.copyOf(Objects.requireNonNull(observations, "observations"));
        if (samples.isEmpty()) throw new IllegalArgumentException("calibration dataset cannot be empty");
        for (var sample : samples) if (!scope.equals(sample.scopeId()) || !target.equals(sample.targetId()))
            throw new SecurityException("calibration dataset mixes scopes or target tasks");
        return samples;
    }
    private static void ensureIndependentSampleIds(List<UncertaintyCalibrationObservation> samples) {
        var ids = new HashSet<String>();
        for (var sample : samples) if (!ids.add(sample.sampleId()))
            throw new SecurityException("duplicate calibration sample ID: " + sample.sampleId());
        var evidence = new HashSet<String>();
        for (var sample : samples) {
            String key = sample.labelEvidence().evidenceType() + ":" + sample.labelEvidence().evidenceId();
            if (!evidence.add(key)) throw new SecurityException("a labelled evidence record cannot be reused as multiple calibration samples");
        }
    }
    private static EnumMap<UncertaintyDimension, List<UncertaintyCalibrationObservation>> group(List<UncertaintyCalibrationObservation> samples) {
        var groups = new EnumMap<UncertaintyDimension, List<UncertaintyCalibrationObservation>>(UncertaintyDimension.class);
        for (var d : UncertaintyDimension.values()) groups.put(d, new ArrayList<>());
        samples.forEach(sample -> groups.get(sample.dimension()).add(sample)); return groups;
    }
    private static List<UncertaintyCalibrationBin> buildBins(List<UncertaintyCalibrationObservation> samples) {
        int[] counts = new int[10]; double[] predictionSums = new double[10]; long[] errors = new long[10];
        for (var sample : samples) {
            int index = bin(sample.predictedUncertainty()); counts[index]++;
            predictionSums[index] += sample.predictedUncertainty(); if (sample.realizedError()) errors[index]++;
        }
        var result = new ArrayList<UncertaintyCalibrationBin>(10);
        for (int i = 0; i < 10; i++) {
            double mean = counts[i] == 0 ? 0 : predictionSums[i] / counts[i];
            // Beta(1,1) smoothing avoids extreme 0/1 outputs in small but adequately sized bins.
            double calibrated = counts[i] == 0 ? 0 : (errors[i] + 1.0) / (counts[i] + 2.0);
            result.add(new UncertaintyCalibrationBin(i, counts[i], mean, errors[i], calibrated));
        }
        return List.copyOf(result);
    }
    private static int bin(double value) { return Math.min(9, (int) Math.floor(value * 10.0)); }

    private static java.util.Set<String> sampleIdHashes(List<UncertaintyCalibrationObservation> samples) {
        return samples.stream().map(sample -> hash("sample:" + sample.sampleId())).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private static java.util.Set<String> evidenceHashes(List<UncertaintyCalibrationObservation> samples) {
        return samples.stream().map(sample -> hash("evidence:" + sample.labelEvidence().evidenceType()
                + ":" + sample.labelEvidence().evidenceId())).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }

    public static String fingerprint(List<UncertaintyCalibrationObservation> samples) {
        String canonical = samples.stream().sorted(Comparator.comparing(UncertaintyCalibrationObservation::sampleId))
                .map(s -> s.sampleId()+"|"+s.scopeId().value()+"|"+s.targetId()+"|"+s.dimension()+"|"
                        +Double.toHexString(s.predictedUncertainty())+"|"+s.realizedError()+"|"
                        +s.labelEvidence().evidenceType()+":"+s.labelEvidence().evidenceId()+"|"+s.observedAt())
                .reduce((a,b)->a+"\n"+b).orElse("");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    public record ValidationResult(UncertaintyCalibrationProfile profile, double expectedCalibrationError,
                                   long validationSampleCount, String validationDatasetFingerprint) { }
}
