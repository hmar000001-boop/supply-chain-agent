package dev.supplychain.agent.mechanisms.b3;

@FunctionalInterface
public interface WorkspaceConsumer { WorkspaceConsumerReceipt consume(WorkspaceBroadcast broadcast); }
