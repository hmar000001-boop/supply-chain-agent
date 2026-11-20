package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.actions.api.ActionRegistry;
import dev.supplychain.agent.memory.common.EvidenceReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Compiles only trusted typed references from a verified B2 child result. Narrative findings are
 * retained as evidence references only; no text is parsed into action IDs, arguments or instructions.
 */
public final class RecoveryProcedureDraftService {
    private final ImpasseResolutionService impasses;
    private final ActionRegistry actions;
    private final RecoveryProcedureDraftRepository repository;
    private final RecoveryDraftEvidenceVerifier evidenceVerifier;
    private final RecoveryProcedureApprovalAuthority approvalAuthority;

    public RecoveryProcedureDraftService(ImpasseResolutionService impasses, ActionRegistry actions,
            RecoveryProcedureDraftRepository repository, RecoveryDraftEvidenceVerifier evidenceVerifier,
            RecoveryProcedureApprovalAuthority approvalAuthority) {
        this.impasses=Objects.requireNonNull(impasses,"impasses"); this.actions=Objects.requireNonNull(actions,"actions");
        this.repository=Objects.requireNonNull(repository,"repository"); this.evidenceVerifier=Objects.requireNonNull(evidenceVerifier,"evidenceVerifier");
        this.approvalAuthority=Objects.requireNonNull(approvalAuthority,"approvalAuthority");
    }

    public RecoveryProcedureDraft createDraft(RecoveryProcedureDraftRequest request, Instant now) {
        Objects.requireNonNull(request,"request"); Objects.requireNonNull(now,"now");
        ImpasseResolutionCase impasse=impasses.get(request.impasseCaseId());
        if(impasse.version()!=request.expectedCaseVersion()) throw new IllegalStateException("impasse case version changed");
        if(impasse.status()!=ImpasseCaseStatus.RESOLVED || impasse.result()==null
                || impasse.result().disposition()==ResolutionDisposition.UNRESOLVED)
            throw new IllegalStateException("only a verified resolved child case can produce a procedure draft");
        ParentContextSnapshot parent=request.currentParent();
        if(!impasse.scopeId().equals(request.scopeId()) || !impasse.scopeId().equals(parent.scopeId())
                || !impasse.parentRequestId().equals(parent.requestId())
                || impasse.parentWorkingMemoryVersion()!=parent.workingMemoryVersion()
                || !impasse.objectiveFingerprint().equals(parent.objectiveFingerprint()))
            throw new SecurityException("parent scope, request, version or objective changed since child resolution");
        if(now.isBefore(impasse.openedAt()) || !now.isBefore(request.expiresAt()) || request.expiresAt().isAfter(impasse.deadline()))
            throw new IllegalArgumentException("draft time/expiry is outside the resolved case interval");

        List<EvidenceReference> evidence=impasse.result().findings().stream().flatMap(f->f.evidence().stream()).distinct().toList();
        if(evidence.isEmpty() || !evidenceVerifier.verify(request.scopeId(),evidence,now))
            throw new SecurityException("child findings contain evidence that is missing, stale or outside scope");
        List<RegisteredRecoveryActionReference> actionRefs=request.actionIds().stream().map(this::registeredReference).toList();
        String fingerprint=fingerprint(impasse,request,actionRefs,evidence);
        RecoveryProcedureDraft draft=new RecoveryProcedureDraft(request.draftId(),request.scopeId(),impasse.executionId(),impasse.id(),
                impasse.parentRequestId(),impasse.parentWorkingMemoryVersion(),impasse.objectiveFingerprint(),request.conditionKey(),
                actionRefs,request.preconditionKeys(),request.expectedPostconditionKeys(),request.failureConditionKeys(),evidence,
                fingerprint,0,RecoveryProcedureStatus.DRAFT,now,now,request.expiresAt(),null,null,null);
        var existing=repository.findById(draft.id());
        if(existing.isPresent()) {
            if(existing.get().sameSource(draft)) return existing.get();
            throw new SecurityException("draft ID is already bound to a different source or proposal");
        }
        try { repository.saveNew(draft); return draft; }
        catch(IllegalStateException concurrent) {
            var raced=repository.findById(draft.id()).orElseThrow(()->concurrent);
            if(raced.sameSource(draft)) return raced;
            throw new SecurityException("draft ID raced with a different proposal",concurrent);
        }
    }

    /** This authorizes review state only; it does not dispatch or execute any action. */
    public RecoveryProcedureDraft approve(String id,long expectedVersion,ParentContextSnapshot currentParent,
                                          RecoveryProcedureApproval approval,Instant now) {
        RecoveryProcedureDraft current=get(id);
        if(current.version()!=expectedVersion) throw new IllegalStateException("recovery draft version conflict");
        requireParent(current,currentParent);
        if(!evidenceVerifier.verify(current.scopeId(),current.evidence(),now))
            throw new SecurityException("draft evidence is no longer valid");
        for(RegisteredRecoveryActionReference ref:current.actions()) {
            RegisteredRecoveryActionReference fresh=registeredReference(new ActionId(ref.actionId()));
            if(!fresh.equals(ref)) throw new SecurityException("registered action descriptor changed since draft creation");
        }
        if(approval.approvedAt().isAfter(now)) throw new IllegalArgumentException("approval timestamp cannot be in the future");
        if(!approvalAuthority.isAuthorized(current,approval.approverId(),approval.approvalReference(),now))
            throw new SecurityException("recovery procedure approval is not authorized");
        RecoveryProcedureDraft next=current.approve(approval.approverId(),approval.approvalReference(),approval.approvedAt());
        if(!repository.compareAndSet(id,expectedVersion,next)) throw new IllegalStateException("recovery draft changed concurrently");
        return next;
    }

    public RecoveryProcedureDraft reject(String id,long expectedVersion,Instant at) {
        RecoveryProcedureDraft current=get(id);
        if(current.version()!=expectedVersion) throw new IllegalStateException("recovery draft version conflict");
        RecoveryProcedureDraft next=current.reject(at);
        if(!repository.compareAndSet(id,expectedVersion,next)) throw new IllegalStateException("recovery draft changed concurrently");
        return next;
    }

    public RecoveryProcedureDraft expire(String id,long expectedVersion,Instant at) {
        RecoveryProcedureDraft current=get(id);
        if(current.version()!=expectedVersion) throw new IllegalStateException("recovery draft version conflict");
        RecoveryProcedureDraft next=current.expire(at);
        if(next==current) return current;
        if(!repository.compareAndSet(id,expectedVersion,next)) throw new IllegalStateException("recovery draft changed concurrently");
        return next;
    }

    public RecoveryProcedureDraft get(String id) { return repository.findById(id).orElseThrow(()->new IllegalArgumentException("unknown recovery procedure draft: "+id)); }

    private RegisteredRecoveryActionReference registeredReference(ActionId id) {
        ActionDescriptor d=actions.descriptor(id);
        String canonical=d.id().value()+"|"+d.name()+"|"+d.description()+"|"+d.category()+"|"+d.effect()+"|"+d.requiredCapability()+"|"+d.idempotencyRequired();
        return new RegisteredRecoveryActionReference(d.id().value(),d.requiredCapability(),d.effect(),sha256(canonical));
    }
    private static void requireParent(RecoveryProcedureDraft draft,ParentContextSnapshot parent) {
        Objects.requireNonNull(parent,"currentParent");
        if(!draft.scopeId().equals(parent.scopeId()) || !draft.parentRequestId().equals(parent.requestId())
                || draft.parentWorkingMemoryVersion()!=parent.workingMemoryVersion()
                || !draft.objectiveFingerprint().equals(parent.objectiveFingerprint()))
            throw new SecurityException("parent has changed; procedure approval is no longer valid");
    }
    private static String fingerprint(ImpasseResolutionCase c,RecoveryProcedureDraftRequest r,
            List<RegisteredRecoveryActionReference> actions,List<EvidenceReference> evidence) {
        String canonical=c.id()+"|"+c.scopeId().value()+"|"+c.executionId()+"|"+c.parentRequestId()+"|"+c.parentWorkingMemoryVersion()+"|"+c.objectiveFingerprint()
                +"|"+r.conditionKey()+"|"+actions+"|"+r.preconditionKeys()+"|"+r.expectedPostconditionKeys()+"|"+r.failureConditionKeys()+"|"+evidence+"|"+r.expiresAt();
        return sha256(canonical);
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable",impossible); }
    }
}
