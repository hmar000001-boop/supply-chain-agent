package dev.supplychain.agent.mechanisms.b3;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Explicit group of admitted content items whose joint coverage contributes complementary workspace value. */
public record WorkspaceCoalition(String id,List<String> candidateIds,Set<String> jointCoverageTags,
                                 int contextUnits,double aggregateMarginalScore){
    public WorkspaceCoalition{
        id=require(id,"id");candidateIds=List.copyOf(candidateIds);jointCoverageTags=Set.copyOf(jointCoverageTags);
        if(candidateIds.size()<2||candidateIds.stream().anyMatch(v->v==null||v.isBlank())||candidateIds.stream().distinct().count()!=candidateIds.size())
            throw new IllegalArgumentException("coalition requires at least two unique candidate IDs");
        if(jointCoverageTags.size()<2||jointCoverageTags.stream().anyMatch(v->v==null||v.isBlank()))throw new IllegalArgumentException("coalition must cover at least two distinct tags");
        if(contextUnits<2||!Double.isFinite(aggregateMarginalScore))throw new IllegalArgumentException("invalid coalition accounting");
    }
    private static String require(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
}
