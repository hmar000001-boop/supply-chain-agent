package dev.supplychain.agent.evaluation.experiment.execution;

import java.util.Map;
import java.util.Collections;
import java.util.Objects;
import java.util.TreeMap;

/** One matched baseline/treatment pair on exactly the same generated simulator input. */
public record FullGraphExperimentPair(
        String protocolId,
        String experimentUnitId,
        String scenarioFamily,
        String fixtureProfile,
        String fixtureFingerprint,
        String effectiveInputFingerprint,
        ExperimentMechanism mechanism,
        FullGraphExperimentRunObservation baseline,
        FullGraphExperimentRunObservation treatment,
        Map<String, Double> treatmentMinusBaseline,
        String pairFingerprint) {
    public FullGraphExperimentPair {
        protocolId = required(protocolId, "protocolId");
        experimentUnitId = required(experimentUnitId, "experimentUnitId");
        scenarioFamily = required(scenarioFamily, "scenarioFamily");
        fixtureProfile = required(fixtureProfile, "fixtureProfile");
        fixtureFingerprint = required(fixtureFingerprint, "fixtureFingerprint");
        effectiveInputFingerprint = required(effectiveInputFingerprint, "effectiveInputFingerprint");
        mechanism = Objects.requireNonNull(mechanism, "mechanism");
        baseline = Objects.requireNonNull(baseline, "baseline");
        treatment = Objects.requireNonNull(treatment, "treatment");
        if (baseline.arm() != ExperimentArm.BASELINE || treatment.arm() != ExperimentArm.TREATMENT
                || baseline.mechanism() != mechanism || treatment.mechanism() != mechanism
                || !baseline.experimentUnitId().equals(experimentUnitId)
                || !treatment.experimentUnitId().equals(experimentUnitId)
                || !baseline.fixtureFingerprint().equals(fixtureFingerprint)
                || !treatment.fixtureFingerprint().equals(fixtureFingerprint)
                || !baseline.requestFingerprint().equals(treatment.requestFingerprint())) {
            throw new SecurityException("matched pair contains mismatched graph identity, condition or scenario input");
        }
        var deltas = new TreeMap<String, Double>();
        if (!baseline.metrics().keySet().equals(treatment.metrics().keySet())) {
            throw new IllegalArgumentException("matched arms must emit the same metric keys");
        }
        for (Map.Entry<String, Double> entry : baseline.metrics().entrySet()) {
            double treatmentValue = treatment.metrics().get(entry.getKey());
            deltas.put(entry.getKey(), treatmentValue - entry.getValue());
        }
        treatmentMinusBaseline = Collections.unmodifiableMap(deltas);
        pairFingerprint = required(pairFingerprint, "pairFingerprint");
    }

    public String toJsonLine() {
        String deltas = treatmentMinusBaseline.entrySet().stream()
                .map(entry -> FullGraphExperimentRunObservation.quote(entry.getKey()) + ":" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"recordType\":\"fullGraphMatchedPair\","
                + "\"protocolId\":" + FullGraphExperimentRunObservation.quote(protocolId) + ","
                + "\"experimentUnitId\":" + FullGraphExperimentRunObservation.quote(experimentUnitId) + ","
                + "\"scenarioFamily\":" + FullGraphExperimentRunObservation.quote(scenarioFamily) + ","
                + "\"fixtureProfile\":" + FullGraphExperimentRunObservation.quote(fixtureProfile) + ","
                + "\"fixtureFingerprint\":" + FullGraphExperimentRunObservation.quote(fixtureFingerprint) + ","
                + "\"effectiveInputFingerprint\":" + FullGraphExperimentRunObservation.quote(effectiveInputFingerprint) + ","
                + "\"mechanism\":" + FullGraphExperimentRunObservation.quote(mechanism.name()) + ","
                + "\"baseline\":" + baseline.toJsonLine() + ","
                + "\"treatment\":" + treatment.toJsonLine() + ","
                + "\"treatmentMinusBaseline\":{" + deltas + "},"
                + "\"pairFingerprint\":" + FullGraphExperimentRunObservation.quote(pairFingerprint) + "}";
    }

    private static String required(String value, String field) {
        String result = Objects.requireNonNull(value, field).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return result;
    }
}
