package dev.supplychain.agent.mechanisms.b3;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Explicit synchronous broadcast adapter; durable delivery/outbox is deferred to M7. */
public final class WorkspaceBroadcastService{
    private final Map<String,WorkspaceConsumer> consumers;
    private final WorkspaceReceiptRecorder receiptRecorder;
    public WorkspaceBroadcastService(Map<String,WorkspaceConsumer> consumers){this(consumers,WorkspaceReceiptRecorder.noOp());}
    public WorkspaceBroadcastService(Map<String,WorkspaceConsumer> consumers, WorkspaceReceiptRecorder receiptRecorder){
        this.consumers=Map.copyOf(Objects.requireNonNull(consumers,"consumers"));
        this.consumers.forEach((id,c)->{if(id==null||id.isBlank()||c==null)throw new IllegalArgumentException("consumer registry contains invalid entry");});
        this.receiptRecorder=Objects.requireNonNull(receiptRecorder,"receiptRecorder");
    }
    public WorkspaceBroadcastReport broadcast(WorkspaceSnapshot snapshot,dev.supplychain.agent.memory.common.MemoryScopeId expectedScope,Set<String> consumerIds,Instant at){
        Objects.requireNonNull(snapshot,"snapshot");Objects.requireNonNull(expectedScope,"expectedScope");Objects.requireNonNull(consumerIds,"consumerIds");Objects.requireNonNull(at,"at");
        if(!snapshot.scopeId().equals(expectedScope))throw new SecurityException("workspace broadcast scope does not match caller scope");
        if(snapshot.status()!=WorkspaceStatus.ADMITTED)throw new IllegalStateException("only an admitted workspace can be broadcast");
        if(at.isBefore(snapshot.createdAt()))throw new IllegalArgumentException("broadcast cannot predate workspace admission");
        if(consumerIds.isEmpty())throw new IllegalArgumentException("at least one consumer is required");
        var receipts=consumerIds.stream().sorted().map(id->{var consumer=consumers.get(id);if(consumer==null)return new WorkspaceConsumerReceipt(id,snapshot.id(),ConsumerReceiptStatus.REJECTED,List.of(),at,"consumer is not registered");
            try{var receipt=consumer.consume(new WorkspaceBroadcast(snapshot,id,at));if(!id.equals(receipt.consumerId())||!snapshot.id().equals(receipt.workspaceId()))return new WorkspaceConsumerReceipt(id,snapshot.id(),ConsumerReceiptStatus.FAILED,List.of(),at,"consumer returned mismatched receipt identity");
                var admittedIds=snapshot.entries().stream().map(e->e.candidate().id()).collect(java.util.stream.Collectors.toSet());
                if(!admittedIds.containsAll(receipt.consumedCandidateIds()))return new WorkspaceConsumerReceipt(id,snapshot.id(),ConsumerReceiptStatus.FAILED,List.of(),at,"consumer acknowledged candidate outside workspace snapshot");
                return receipt;}
            catch(RuntimeException ex){return new WorkspaceConsumerReceipt(id,snapshot.id(),ConsumerReceiptStatus.FAILED,List.of(),at,"consumer failed: "+ex.getClass().getSimpleName());}}).sorted(Comparator.comparing(WorkspaceConsumerReceipt::consumerId)).toList();
        // Receipt persistence is after identity validation. If it fails, do not silently report
        // success or replay a consumer call: the caller must reconcile the stable receipt ID.
        for (WorkspaceConsumerReceipt receipt : receipts) {
            try { receiptRecorder.record(snapshot, receipt); }
            catch (RuntimeException failure) {
                throw new IllegalStateException("workspace consumer receipt could not be durably recorded; reconcile receipt "
                        + B3OutcomeEvidenceService.receiptId(snapshot.id(), receipt.consumerId()), failure);
            }
        }
        return new WorkspaceBroadcastReport(snapshot.id(),receipts);
    }
}
