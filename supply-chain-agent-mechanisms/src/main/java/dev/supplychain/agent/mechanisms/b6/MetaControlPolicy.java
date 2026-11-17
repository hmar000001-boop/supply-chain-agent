package dev.supplychain.agent.mechanisms.b6;

public record MetaControlPolicy(double evidenceRequestUncertainty,double humanReviewUncertainty,
                                double lowResourceRemainingFraction){
    public MetaControlPolicy{if(!unit(evidenceRequestUncertainty)||!unit(humanReviewUncertainty)||humanReviewUncertainty<evidenceRequestUncertainty||!unit(lowResourceRemainingFraction))throw new IllegalArgumentException("invalid meta-control thresholds");}
    private static boolean unit(double v){return Double.isFinite(v)&&v>=0&&v<=1;}
}
