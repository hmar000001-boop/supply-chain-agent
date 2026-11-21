package dev.supplychain.agent.mechanisms.b6;

/** Immutable reliability bin. The calibrated output estimates the observed error frequency. */
public record UncertaintyCalibrationBin(int index, long sampleCount, double meanPredictedUncertainty,
                                        long realizedErrors, double calibratedUncertainty) {
    public UncertaintyCalibrationBin {
        if (index < 0 || index >= 10 || sampleCount < 0 || realizedErrors < 0 || realizedErrors > sampleCount)
            throw new IllegalArgumentException("invalid calibration-bin counters");
        if (!unit(meanPredictedUncertainty) || !unit(calibratedUncertainty))
            throw new IllegalArgumentException("calibration-bin values must be in [0,1]");
        if (sampleCount == 0 && (meanPredictedUncertainty != 0 || realizedErrors != 0 || calibratedUncertainty != 0))
            throw new IllegalArgumentException("empty calibration bin cannot contain values");
    }
    private static boolean unit(double v) { return Double.isFinite(v) && v >= 0 && v <= 1; }
}
