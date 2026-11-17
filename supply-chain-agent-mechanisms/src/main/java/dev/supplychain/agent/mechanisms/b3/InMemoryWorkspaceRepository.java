package dev.supplychain.agent.mechanisms.b3;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Reference adapter; M7 will provide durable storage. */
public final class InMemoryWorkspaceRepository implements WorkspaceRepository{
    private final ConcurrentHashMap<String,WorkspaceSnapshot> values=new ConcurrentHashMap<>();
    public void saveNew(WorkspaceSnapshot s){if(values.putIfAbsent(s.id(),s)!=null)throw new IllegalStateException("workspace already exists");}
    public Optional<WorkspaceSnapshot> findById(String id){return Optional.ofNullable(values.get(id));}
    public boolean compareAndSet(String id,long expected,WorkspaceSnapshot replacement){final boolean[] changed={false};values.compute(id,(k,current)->{if(current==null)throw new IllegalArgumentException("unknown workspace: "+id);if(current.version()!=expected)return current;if(replacement.version()!=expected+1||!replacement.id().equals(id))throw new IllegalArgumentException("invalid workspace replacement");changed[0]=true;return replacement;});return changed[0];}
}
