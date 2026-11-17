package dev.supplychain.agent.mechanisms.b1;

public record ActivationEstimate(double baseLevel, double associativeStrength, double totalActivation) {
    public ActivationEstimate {
        if (!Double.isFinite(baseLevel) || !Double.isFinite(associativeStrength) || !Double.isFinite(totalActivation)) {
            throw new IllegalArgumentException("activation components must be finite");
        }
        if (Math.abs((baseLevel + associativeStrength) - totalActivation) > 1e-9) {
            throw new IllegalArgumentException("total activation must equal base plus associative strength");
        }
    }
}
