package dev.supplychain.agent.mechanisms.b6;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record ResourceBudgetSnapshot(String executionId,ResourceBudget budget,Map<ResourceUnit,Long> consumed,
                                     Map<ResourceUnit,Long> reserved,Instant observedAt){
    public ResourceBudgetSnapshot{
        executionId=Objects.requireNonNull(executionId,"executionId").trim();if(executionId.isEmpty())throw new IllegalArgumentException("executionId cannot be blank");
        budget=Objects.requireNonNull(budget,"budget");observedAt=Objects.requireNonNull(observedAt,"observedAt");
        consumed=validated(consumed);reserved=validated(reserved);
    }
    private static Map<ResourceUnit,Long> validated(Map<ResourceUnit,Long> source){
        var copy=new EnumMap<ResourceUnit,Long>(ResourceUnit.class);Objects.requireNonNull(source,"resource amounts").forEach((u,v)->{if(u==null||v==null||v<0)throw new IllegalArgumentException("resource amounts must be nonnegative");copy.put(u,v);});return Map.copyOf(copy);
    }
    public long remaining(ResourceUnit unit){long limit=budget.limit(unit);if(limit==Long.MAX_VALUE)return Long.MAX_VALUE;long used=consumed.getOrDefault(unit,0L),held=reserved.getOrDefault(unit,0L);if(used>=limit||held>=limit-used)return 0;return limit-used-held;}
    public boolean deadlineExceeded(){return !observedAt.isBefore(budget.deadline());}
    public boolean exhausted(){return java.util.Arrays.stream(ResourceUnit.values()).anyMatch(u->budget.limit(u)!=Long.MAX_VALUE&&remaining(u)==0);}
    public boolean overBudget(){return java.util.Arrays.stream(ResourceUnit.values()).anyMatch(u->{long limit=budget.limit(u);if(limit==Long.MAX_VALUE)return false;long used=consumed.getOrDefault(u,0L),held=reserved.getOrDefault(u,0L);return used>limit||held>limit-used;});}
}
