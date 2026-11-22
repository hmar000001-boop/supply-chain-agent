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
import java.util.TreeMap;

/**
 * Descriptive B6 transfer report over the preregistered TRANSFER split. The report deliberately
 * has no activation semantics and is not a substitute for the HOLDOUT confirmation.
 */
public record B6TransferEvaluationReport(
        int totalSamples,
        Map<UncertaintyDimension, DimensionMetrics> byDimension,
        Map<String, FamilyMetrics> byFamily,
        String evidenceClass,
        String reportFingerprint) {

    public record DimensionMetrics(int sampleCount, double rawExpectedCalibrationError,
            double shadowExpectedCalibrationError, double rawBrierScore, double shadowBrierScore) {
        public DimensionMetrics {
            if (sampleCount < 60 || !finite01(rawExpectedCalibrationError) || !finite01(shadowExpectedCalibrationError)
                    || !finite01(rawBrierScore) || !finite01(shadowBrierScore)) {
                throw new IllegalArgumentException("invalid B6 TRANSFER dimension metrics or minimum sample count");
            }
        }
        private static boolean finite01(double value) { return Double.isFinite(value) && value >= 0.0 && value <= 1.0; }
    }

    public record FamilyMetrics(int sampleCount, double rawBrierScore, double shadowBrierScore) {
        public FamilyMetrics {
            if (sampleCount < 1 || !finite01(rawBrierScore) || !finite01(shadowBrierScore))
                throw new IllegalArgumentException("invalid B6 transfer family metrics");
        }
        private static boolean finite01(double value) { return Double.isFinite(value) && value >= 0.0 && value <= 1.0; }
    }

    public B6TransferEvaluationReport {
        if (totalSamples != 210 * UncertaintyDimension.values().length)
            throw new IllegalArgumentException("B6 TRANSFER report must contain exactly three labels for each of 210 transfer units");
        var dimensions = new EnumMap<UncertaintyDimension, DimensionMetrics>(UncertaintyDimension.class);
        Objects.requireNonNull(byDimension, "byDimension").forEach(dimensions::put);
        if (!dimensions.keySet().equals(Set.of(UncertaintyDimension.values()))
                || dimensions.values().stream().mapToInt(DimensionMetrics::sampleCount).sum() != totalSamples)
            throw new IllegalArgumentException("B6 TRANSFER report must cover all uncertainty dimensions and exact sample totals");
        byDimension = Map.copyOf(dimensions);
        var families = new TreeMap<String, FamilyMetrics>();
        Objects.requireNonNull(byFamily, "byFamily").forEach((name, metrics) -> {
            String key = Objects.requireNonNull(name, "family name").trim();
            if (key.isEmpty() || families.putIfAbsent(key, Objects.requireNonNull(metrics, "family metrics")) != null)
                throw new IllegalArgumentException("invalid or duplicate B6 transfer family");
        });
        if (families.size() < 3 || families.values().stream().mapToInt(FamilyMetrics::sampleCount).sum() != 210)
            throw new IllegalArgumentException("B6 TRANSFER report must cover at least three exact scenario families and 210 units");
        byFamily = Map.copyOf(families);
        evidenceClass = Objects.requireNonNull(evidenceClass, "evidenceClass");
        if (!"SIMULATOR_ONLY_NOT_REAL_WORLD_TRANSFER_EVIDENCE".equals(evidenceClass))
            throw new SecurityException("B6 TRANSFER report must preserve the simulator-only evidence boundary");
        reportFingerprint = ExperimentEvidenceFingerprint.requireSha(reportFingerprint, "reportFingerprint");
        if (!reportFingerprint.equals(calculate(totalSamples, byDimension, byFamily, evidenceClass)))
            throw new SecurityException("B6 TRANSFER report fingerprint does not reproduce its metrics");
    }

    public static B6TransferEvaluationReport fromPairs(
            List<MechanismSpecificExperimentPair> pairs, Map<String, String> familyByUnitFingerprint) {
        List<MechanismSpecificExperimentPair> supplied = List.copyOf(Objects.requireNonNull(pairs, "pairs"));
        Map<String, String> families = Map.copyOf(Objects.requireNonNull(familyByUnitFingerprint, "familyByUnitFingerprint"));
        if (supplied.size() != 210 || families.size() != 210)
            throw new SecurityException("B6 TRANSFER requires the exact 210 registered unit/family mappings");

        var samplesByDimension = new EnumMap<UncertaintyDimension, List<Sample>>(UncertaintyDimension.class);
        for (UncertaintyDimension dimension : UncertaintyDimension.values()) samplesByDimension.put(dimension, new ArrayList<>());
        var familySamples = new TreeMap<String, List<Sample>>();
        var unitFingerprints = new HashSet<String>();
        var sampleIds = new HashSet<String>();
        var evidenceIds = new HashSet<String>();

        for (MechanismSpecificExperimentPair pair : supplied) {
            if (pair.graphPair().mechanism() != ExperimentMechanism.B6_UNCERTAINTY_CALIBRATION)
                throw new SecurityException("B6 TRANSFER report contains a non-B6 experiment pair");
            String unitFingerprint = pair.graphPair().effectiveInputFingerprint();
            String family = families.get(unitFingerprint);
            if (family == null || family.isBlank() || !unitFingerprints.add(unitFingerprint))
                throw new SecurityException("B6 TRANSFER pair is not uniquely bound to its preregistered family");
            B6CalibrationRunEvidence baseline = (B6CalibrationRunEvidence) pair.baselineMechanismEvidence();
            B6CalibrationRunEvidence treatment = (B6CalibrationRunEvidence) pair.treatmentMechanismEvidence();
            if (pair.b6ValidationLabels().size() != UncertaintyDimension.values().length)
                throw new SecurityException("B6 TRANSFER pair must carry one independently verified label per dimension");
            var pairDimensions = new HashSet<UncertaintyDimension>();
            for (B6SimulatorLabelEvidence label : pair.b6ValidationLabels()) {
                var observation = label.observation();
                String evidenceId = observation.labelEvidence().evidenceType() + ":" + observation.labelEvidence().evidenceId();
                if (label.split() != dev.supplychain.agent.evaluation.experiment.scenario.ExperimentSplit.TRANSFER
                        || !label.experimentUnitFingerprint().equals(unitFingerprint)
                        || !pairDimensions.add(observation.dimension())
                        || !sampleIds.add(observation.sampleId()) || !evidenceIds.add(evidenceId))
                    throw new SecurityException("B6 TRANSFER labels contain split/unit/dimension mismatch or reused evidence");
                double raw = baseline.rawValues().get(observation.dimension());
                double shadow = treatment.observedValues().get(observation.dimension());
                if (Double.compare(raw, observation.predictedUncertainty()) != 0
                        || !Double.isFinite(shadow) || shadow < 0.0 || shadow > 1.0)
                    throw new SecurityException("B6 TRANSFER label does not match its raw prediction or shadow value");
                Sample sample = new Sample(raw, shadow, observation.realizedError());
                samplesByDimension.get(observation.dimension()).add(sample);
                familySamples.computeIfAbsent(family, ignored -> new ArrayList<>()).add(sample);
            }
            if (!pairDimensions.equals(Set.of(UncertaintyDimension.values())))
                throw new SecurityException("B6 TRANSFER pair does not cover every uncertainty dimension");
        }
        if (!unitFingerprints.equals(families.keySet()))
            throw new SecurityException("B6 TRANSFER pairs do not cover the exact family mapping");

        var dimensionMetrics = new EnumMap<UncertaintyDimension, DimensionMetrics>(UncertaintyDimension.class);
        for (UncertaintyDimension dimension : UncertaintyDimension.values()) {
            List<Sample> values = samplesByDimension.get(dimension);
            if (values.size() < 60)
                throw new SecurityException("B6 TRANSFER requires at least 60 independently labelled samples for " + dimension);
            dimensionMetrics.put(dimension, new DimensionMetrics(values.size(), ece(values, false), ece(values, true),
                    brier(values, false), brier(values, true)));
        }
        var familyMetrics = new TreeMap<String, FamilyMetrics>();
        familySamples.forEach((family, values) -> familyMetrics.put(family,
                new FamilyMetrics(values.size() / UncertaintyDimension.values().length, brier(values, false), brier(values, true))));
        String evidenceClass = "SIMULATOR_ONLY_NOT_REAL_WORLD_TRANSFER_EVIDENCE";
        return new B6TransferEvaluationReport(210 * UncertaintyDimension.values().length,
                dimensionMetrics, familyMetrics, evidenceClass,
                calculate(210 * UncertaintyDimension.values().length, dimensionMetrics, familyMetrics, evidenceClass));
    }

    public String toJson() {
        String dimensions = byDimension.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> "\"" + entry.getKey().name() + "\":{" +
                        "\"sampleCount\":" + entry.getValue().sampleCount() + "," +
                        "\"rawECE\":" + entry.getValue().rawExpectedCalibrationError() + "," +
                        "\"shadowECE\":" + entry.getValue().shadowExpectedCalibrationError() + "," +
                        "\"rawBrier\":" + entry.getValue().rawBrierScore() + "," +
                        "\"shadowBrier\":" + entry.getValue().shadowBrierScore() + "}")
                .collect(java.util.stream.Collectors.joining(","));
        String families = byFamily.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> "\"" + escape(entry.getKey()) + "\":{" +
                        "\"sampleCount\":" + entry.getValue().sampleCount() + "," +
                        "\"rawBrier\":" + entry.getValue().rawBrierScore() + "," +
                        "\"shadowBrier\":" + entry.getValue().shadowBrierScore() + "}")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"totalSamples\":" + totalSamples + ",\"evidenceClass\":\"" + evidenceClass
                + "\",\"byDimension\":{" + dimensions + "},\"byFamily\":{" + families
                + "},\"reportFingerprint\":\"" + reportFingerprint + "\"}";
    }

    private record Sample(double raw, double shadow, boolean error) { }
    private static double ece(List<Sample> samples, boolean shadow) {
        int[] counts = new int[10]; double[] predictions = new double[10]; int[] errors = new int[10];
        for (Sample sample : samples) {
            double value = shadow ? sample.shadow() : sample.raw();
            int bin = Math.min(9, (int) Math.floor(value * 10.0));
            counts[bin]++; predictions[bin] += value; if (sample.error()) errors[bin]++;
        }
        double result = 0.0;
        for (int bin = 0; bin < 10; bin++) if (counts[bin] > 0) {
            result += ((double) counts[bin] / samples.size())
                    * Math.abs(predictions[bin] / counts[bin] - (double) errors[bin] / counts[bin]);
        }
        return result;
    }
    private static double brier(List<Sample> samples, boolean shadow) {
        return samples.stream().mapToDouble(sample -> {
            double prediction = shadow ? sample.shadow() : sample.raw();
            double target = sample.error() ? 1.0 : 0.0;
            return (prediction - target) * (prediction - target);
        }).average().orElseThrow();
    }
    private static String calculate(int total, Map<UncertaintyDimension, DimensionMetrics> dimensions,
            Map<String, FamilyMetrics> families, String evidenceClass) {
        String canonicalDimensions = dimensions.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue().sampleCount() + ":"
                        + Double.toHexString(entry.getValue().rawExpectedCalibrationError()) + ":"
                        + Double.toHexString(entry.getValue().shadowExpectedCalibrationError()) + ":"
                        + Double.toHexString(entry.getValue().rawBrierScore()) + ":"
                        + Double.toHexString(entry.getValue().shadowBrierScore()))
                .collect(java.util.stream.Collectors.joining("|"));
        String canonicalFamilies = new TreeMap<>(families).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().sampleCount() + ":"
                        + Double.toHexString(entry.getValue().rawBrierScore()) + ":"
                        + Double.toHexString(entry.getValue().shadowBrierScore()))
                .collect(java.util.stream.Collectors.joining("|"));
        return ExperimentEvidenceFingerprint.sha256(total + "|" + evidenceClass + "|" + canonicalDimensions + "|" + canonicalFamilies);
    }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
}
