package dev.supplychain.agent.mechanisms.b6;

import java.util.Objects;

public record UncertaintySignal(UncertaintyDimension dimension,double uncertainty,String rationale,int evidenceCount){
    public UncertaintySignal{dimension=Objects.requireNonNull(dimension,"dimension");if(!Double.isFinite(uncertainty)||uncertainty<0||uncertainty>1)throw new IllegalArgumentException("uncertainty must be in [0,1]");rationale=Objects.requireNonNull(rationale,"rationale").trim();if(rationale.isEmpty())throw new IllegalArgumentException("rationale cannot be blank");if(evidenceCount<0)throw new IllegalArgumentException("evidenceCount cannot be negative");}
}
