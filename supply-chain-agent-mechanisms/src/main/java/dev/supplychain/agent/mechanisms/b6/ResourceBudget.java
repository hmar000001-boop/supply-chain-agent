package dev.supplychain.agent.mechanisms.b6;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record ResourceBudget(Map<ResourceUnit,Long> limits,Instant deadline){
    public ResourceBudget{var copy=new EnumMap<ResourceUnit,Long>(ResourceUnit.class);Objects.requireNonNull(limits,"limits").forEach((k,v)->{if(k==null||v==null||v<0)throw new IllegalArgumentException("resource limits must be nonnegative");copy.put(k,v);});limits=Map.copyOf(copy);deadline=Objects.requireNonNull(deadline,"deadline");}
    public long limit(ResourceUnit unit){return limits.getOrDefault(unit,Long.MAX_VALUE);}
}
