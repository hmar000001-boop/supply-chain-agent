package dev.supplychain.agent.mechanisms.b3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Bounded admission with mandatory-content protection, explicit conflicts and complementary coverage. */
public final class WorkspaceAdmissionService{
    private final WorkspaceRepository repository;
    public WorkspaceAdmissionService(WorkspaceRepository repository){this.repository=Objects.requireNonNull(repository,"repository");}
    public WorkspaceAdmissionOutcome admit(WorkspaceAdmissionRequest request){
        Objects.requireNonNull(request,"request");
        var selected=new ArrayList<WorkspaceEntry>();var selectedIds=new HashSet<String>();var covered=new LinkedHashSet<String>();int used=0;
        var mandatory=request.candidates().stream().filter(WorkspaceCandidate::mandatory).sorted(Comparator.comparing(WorkspaceCandidate::id)).toList();
        if(mandatory.size()>request.maximumCandidates()) return failedMandatory("mandatory candidates exceed maximum candidate count");
        for(var candidate:mandatory){
            if(conflicts(candidate,selected)) return failedMandatory("mandatory candidates conflict: "+candidate.id());
            if(candidate.contextUnits()>request.capacityUnits()-used)return failedMandatory("mandatory candidates exceed workspace capacity");
            var newTags=new LinkedHashSet<>(candidate.coverageTags());newTags.removeAll(covered);
            selected.add(new WorkspaceEntry(candidate,request.scoringPolicy().baseScore(candidate),newTags));
            selectedIds.add(candidate.id());covered.addAll(candidate.coverageTags());used+=candidate.contextUnits();
        }
        var remaining=new ArrayList<>(request.candidates().stream().filter(c->!selectedIds.contains(c.id())&&!c.mandatory()).toList());
        while(selected.size()<request.maximumCandidates()){
            WorkspaceCandidate best=null;double bestScore=Double.NEGATIVE_INFINITY;Set<String> bestNew=Set.of();
            for(var candidate:remaining){
                if(candidate.contextUnits()>request.capacityUnits()-used||conflicts(candidate,selected))continue;
                var fresh=new LinkedHashSet<>(candidate.coverageTags());fresh.removeAll(covered);
                double bonus=fresh.stream().mapToDouble(tag->request.coverageValue().getOrDefault(tag,0.0)).sum()*request.scoringPolicy().coverageBonusPerUnit();
                double score=request.scoringPolicy().baseScore(candidate)+bonus;
                if(score>bestScore||(Double.compare(score,bestScore)==0&&best!=null&&candidate.id().compareTo(best.id())<0)){best=candidate;bestScore=score;bestNew=Set.copyOf(fresh);}
            }
            if(best==null||bestScore<=0.0)break;
            selected.add(new WorkspaceEntry(best,bestScore,bestNew));selectedIds.add(best.id());covered.addAll(best.coverageTags());used+=best.contextUnits();remaining.remove(best);
        }
        String id=UUID.randomUUID().toString();
        var coalitionMembers=selected.stream().filter(entry->!entry.newlyCoveredTags().isEmpty()).toList();
        var coalitions=List.<WorkspaceCoalition>of();
        var jointTags=coalitionMembers.stream().flatMap(entry->entry.newlyCoveredTags().stream()).collect(java.util.stream.Collectors.toSet());
        if(coalitionMembers.size()>=2&&jointTags.size()>=2){
            var memberIds=coalitionMembers.stream().map(entry->entry.candidate().id()).toList();
            int coalitionUnits=coalitionMembers.stream().mapToInt(entry->entry.candidate().contextUnits()).sum();
            double coalitionScore=coalitionMembers.stream().mapToDouble(WorkspaceEntry::marginalScore).sum();
            coalitions=List.of(new WorkspaceCoalition("coalition:"+id,memberIds,jointTags,coalitionUnits,coalitionScore));
        }
        var snapshot=new WorkspaceSnapshot(id,request.cycleId(),request.scopeId(),request.capacityUnits(),used,selected,coalitions,covered,
                WorkspaceStatus.ADMITTED,0,request.createdAt(),request.createdAt());
        repository.saveNew(snapshot);
        var missing=request.requiredCoverage().stream().filter(t->!covered.contains(t)).sorted().toList();
        return new WorkspaceAdmissionOutcome(missing.isEmpty()?WorkspaceAdmissionStatus.ADMITTED:WorkspaceAdmissionStatus.INCOMPLETE_COVERAGE,
                snapshot,missing,missing.isEmpty()?"mandatory and highest-value compatible content admitted":"workspace admitted; required coverage remains incomplete");
    }
    public WorkspaceSnapshot close(String id,dev.supplychain.agent.memory.common.MemoryScopeId scopeId,long expectedVersion,java.time.Instant at){
        Objects.requireNonNull(scopeId,"scopeId");
        var current=repository.findById(id).orElseThrow(()->new IllegalArgumentException("unknown workspace: "+id));
        if(!current.scopeId().equals(scopeId))throw new SecurityException("workspace scope does not match caller scope");
        if(current.version()!=expectedVersion)throw new IllegalStateException("workspace version conflict");
        var closed=current.close(at);if(closed==current)return current;
        if(!repository.compareAndSet(id,expectedVersion,closed))throw new IllegalStateException("workspace changed concurrently");return closed;
    }
    private static boolean conflicts(WorkspaceCandidate candidate,List<WorkspaceEntry> selected){
        return selected.stream().anyMatch(entry -> candidate.conflictsWith().contains(entry.candidate().id())
                || entry.candidate().conflictsWith().contains(candidate.id()));
    }
    private static WorkspaceAdmissionOutcome failedMandatory(String why){return new WorkspaceAdmissionOutcome(WorkspaceAdmissionStatus.INSUFFICIENT_MANDATORY_CAPACITY,null,List.of(),why);}
}
