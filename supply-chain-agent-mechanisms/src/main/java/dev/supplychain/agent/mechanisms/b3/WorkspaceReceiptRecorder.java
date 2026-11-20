package dev.supplychain.agent.mechanisms.b3;

/** Callback invoked once a consumer receipt has passed the broadcast identity checks. */
@FunctionalInterface
public interface WorkspaceReceiptRecorder {
    void record(WorkspaceSnapshot snapshot, WorkspaceConsumerReceipt receipt);
    static WorkspaceReceiptRecorder noOp() { return (snapshot, receipt) -> { }; }
}
