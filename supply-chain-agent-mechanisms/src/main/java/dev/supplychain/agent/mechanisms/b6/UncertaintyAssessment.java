package dev.supplychain.agent.mechanisms.b6;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** All three uncertainty dimensions must be assessed; absence is never interpreted as zero uncertainty. */
public record UncertaintyAssessment(Map<UncertaintyDimension,UncertaintySignal> signals){
    public UncertaintyAssessment{
        Objects.requireNonNull(signals,"signals");
        var copy=new EnumMap<UncertaintyDimension,UncertaintySignal>(UncertaintyDimension.class);
        copy.putAll(signals);
        for(var dimension:UncertaintyDimension.values()){
            var signal=copy.get(dimension);
            if(signal==null)throw new IllegalArgumentException("missing uncertainty assessment for "+dimension);
            if(signal.dimension()!=dimension)throw new IllegalArgumentException("uncertainty map key does not match signal dimension");
        }
        signals=Map.copyOf(copy);
    }
    public double maximumUncertainty(){return signals.values().stream().mapToDouble(UncertaintySignal::uncertainty).max().orElseThrow();}
    public boolean contains(UncertaintyDimension dimension){return signals.containsKey(dimension);}
}
