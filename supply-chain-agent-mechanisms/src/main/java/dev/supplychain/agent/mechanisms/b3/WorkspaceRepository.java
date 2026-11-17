package dev.supplychain.agent.mechanisms.b3;

import java.util.Optional;

public interface WorkspaceRepository {void saveNew(WorkspaceSnapshot snapshot);Optional<WorkspaceSnapshot> findById(String id);boolean compareAndSet(String id,long expectedVersion,WorkspaceSnapshot replacement);}
