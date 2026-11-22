package dev.supplychain.agent.evaluation.experiment.mechanism;

import dev.supplychain.agent.evaluation.experiment.execution.ExperimentMechanism;
import dev.supplychain.agent.mechanisms.b6.UncertaintyDimension;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Descriptive HOLDOUT calibration report from independently verified simulator labels. */
public record B6HoldoutEvaluationReport(
        int totalSamples,
        Map<UncertaintyDimension, DimensionMetrics> byDimension,
        String evidenceClass,
        String reportFingerprint) {
    public record DimensionMetrics(int sampleCount, double rawExpectedCalibrationError,
            double shadowExpectedCalibrationError, double rawBrierScore, double shadowBrierScore) {
        public DimensionMetrics {
            if (sampleCount < 100 || !finite01(rawExpectedCalibrationError) || !finite01(shadowExpectedCalibrationError)
                    || !finite01(rawBrierScore) || !finite01(shadowBrierScore))
                throw new IllegalArgumentException("invalid B6 HOLDOUT dimension metrics or preregistered sample count");
        }
        private static boolean finite01(double value) { return Double.isFinite(value) && value >= 0 && value <= 1; }
    }

    public B6HoldoutEvaluationReport {
        if (totalSamples < UncertaintyDimension.values().length * 100)
            throw new IllegalArgumentException("B6 HOLDOUT report has fewer than 100 labels per dimension");
        var copy = new EnumMap<UncertaintyDimension, DimensionMetrics>(UncertaintyDimension.class);
        Objects.requireNonNull(byDimension, "byDimension").forEach(copy::put);
        if (!copy.keySet().equals(Set.of(UncertaintyDimension.values())))
            throw new IllegalArgumentException("B6 HOLDOUT report must include every uncertainty dimension");
        byDimension = Map.copyOf(copy);
        if (byDimension.values().stream().mapToInt(DimensionMetrics::sampleCount).sum() != totalSamples)
            throw new IllegalArgumentException("B6 HOLDOUT report sample totals do not reconcile");
        evidenceClass = Objects.requireNonNull(evidenceClass, "evidenceClass");
        if (!"SIMULATOR_ONLY_NOT_REAL_WORLD_CALIBRATION_EVIDENCE".equals(evidenceClass))
            throw new SecurityException("B6 HOLDOUT report must preserve the simulator-only evidence boundary");
        reportFingerprint = ExperimentEvidenceFingerprint.requireSha(reportFingerprint, "reportFingerprint");
        if (!reportFingerprint.equals(calculate(totalSamples, byDimension, evidenceClass)))
            throw new SecurityException("B6 HOLDOUT report fingerprint does not reproduce its metrics");
    }

    public static B6HoldoutEvaluationReport fromPairs(List<MechanismSpecificExperimentPair> pairs) {
        List<MechanismSpecificExperimentPair> supplied = List.copyOf(Objects.requireNonNull(pairs, "pairs"));
        var labelsByDimension = new EnumMap<UncertaintyDimension, List<Sample>>(UncertaintyDimension.class);
        for (UncertaintyDimension dimension : UncertaintyDimension.values()) labelsByDimension.put(dimension, new ArrayList<>());
        Set<String> samples = new HashSet<>();
        Set<String> evidence = new HashSet<>();
        for (MechanismSpecificExperimentPair pair : supplied) {
            if (pair.graphPair().mechanism() != ExperimentMechanism.B6_UNCERTAINTY_CALIBRATION)
                throw new SecurityException("B6 report contains a non-B6 experiment pair");
            var baseline = (B6CalibrationRunEvidence) pair.baselineMechanismEvidence();
            var treatment = (B6CalibrationRunEvidence) pair.treatmentMechanismEvidence();
            for (B6SimulatorLabelEvidence label : pair.b6HoldoutLabels()) {
                var observation = label.observation();
                String evidenceId = observation.labelEvidence().evidenceType() + ":" + observation.labelEvidence().evidenceId();
                if (!samples.add(observation.sampleId()) || !evidence.add(evidenceId))
                    throw new SecurityException("B6 HOLDOUT report reuses a sample ID or labelled evidence record");
                double raw = baseline.rawValues().get(observation.dimension());
                double shadow = treatment.observedValues().get(observation.dimension());
                if (Double.compare(raw, observation.predictedUncertainty()) != 0)
                    throw new SecurityException("B6 HOLDOUT label is not bound to the raw baseline prediction");
                labelsByDimension.get(observation.dimension()).add(new Sample(raw, shadow, observation.realizedError()));
            }
        }
        var metrics = new EnumMap<UncertaintyDimension, DimensionMetrics>(UncertaintyDimension.class);
        int total = 0;
        for (UncertaintyDimension dimension : UncertaintyDimension.values()) {
            List<Sample> values = labelsByDimension.get(dimension);
            if (values.size() < 100) throw new SecurityException("B6 HOLDOUT requires at least 100 independent labels for " + dimension);
            total += values.size();
            metrics.put(dimension, new DimensionMetrics(values.size(), ece(values, false), ece(values, true),
                    brier(values, false), brier(values, true)));
        }
        String evidenceClass = "SIMULATOR_ONLY_NOT_REAL_WORLD_CALIBRATION_EVIDENCE";
        return new B6HoldoutEvaluationReport(total, metrics, evidenceClass,
                calculate(total, metrics, evidenceClass));
    }

    public String toJson() {
        String values = byDimension.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> "\"" + entry.getKey().name() + "\":{" +
                        "\"sampleCount\":" + entry.getValue().sampleCount() + "," +
                        "\"rawECE\":" + entry.getValue().rawExpectedCalibrationError() + "," +
                        "\"shadowECE\":" + entry.getValue().shadowExpectedCalibrationError() + "," +
                        "\"rawBrier\":" + entry.getValue().rawBrierScore() + "," +
                        "\"shadowBrier\":" + entry.getValue().shadowBrierScore() + "}")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"totalSamples\":" + totalSamples + ",\"evidenceClass\":\"" + evidenceClass
                + "\",\"byDimension\":{" + values + "},\"reportFingerprint\":\"" + reportFingerprint + "\"}";
    }

    private record Sample(double raw, double shadow, boolean error) { }
    private static double ece(List<Sample> samples, boolean shadow) {
        int[] counts = new int[10]; double[] predictions = new double[10]; int[] errors = new int[10];
        for (Sample sample : samples) {
            double value = shadow ? sample.shadow() : sample.raw();
            int bin = Math.min(9, (int) Math.floor(value * 10.0));
            counts[bin]++; predictions[bin] += value; if (sample.error()) errors[bin]++;
        }
        double total = 0.0;
        for (int index = 0; index < 10; index++) if (counts[index] > 0) {
            double meanPrediction = predictions[index] / counts[index];
            double observedRate = (double) errors[index] / counts[index];
            total += ((double) counts[index] / samples.size()) * Math.abs(meanPrediction - observedRate);
        }
        return total;
    }
    private static double brier(List<Sample> samples, boolean shadow) {
        return samples.stream().mapToDouble(sample -> {
            double prediction = shadow ? sample.shadow() : sample.raw();
            double target = sample.error() ? 1.0 : 0.0;
            return (prediction - target) * (prediction - target);
        }).average().orElseThrow();
    }
    private static String calculate(int total, Map<UncertaintyDimension, DimensionMetrics> values, String evidenceClass) {
        String canonical = total + "|" + evidenceClass + "|" + values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue().sampleCount() + ":"
                        + Double.toHexString(entry.getValue().rawExpectedCalibrationError()) + ":"
                        + Double.toHexString(entry.getValue().shadowExpectedCalibrationError()) + ":"
                        + Double.toHexString(entry.getValue().rawBrierScore()) + ":"
                        + Double.toHexString(entry.getValue().shadowBrierScore())).toList();
        return ExperimentEvidenceFingerprint.sha256(canonical);
    }
}
