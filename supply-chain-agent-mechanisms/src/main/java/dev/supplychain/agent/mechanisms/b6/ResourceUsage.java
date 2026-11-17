package dev.supplychain.agent.mechanisms.b6;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record ResourceUsage(Map<ResourceUnit,Long> amounts){
    public ResourceUsage{var copy=new EnumMap<ResourceUnit,Long>(ResourceUnit.class);Objects.requireNonNull(amounts,"amounts").forEach((k,v)->{if(k==null||v==null||v<0)throw new IllegalArgumentException("usage must be nonnegative");copy.put(k,v);});amounts=Map.copyOf(copy);}
    public long amount(ResourceUnit unit){return amounts.getOrDefault(unit,0L);}
    public static ResourceUsage of(ResourceUnit unit,long amount){return new ResourceUsage(Map.of(unit,amount));}
}
