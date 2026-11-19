package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleAuthorityPort;

import java.time.Instant;
import java.util.Objects;

/** Durable human decision bound to one exact candidate, intention version and action-plan fingerprint. */
public record ApprovalRecord(String scopeId, String approvalId, CognitiveCycleAuthorityPort.ApprovalBinding binding,
                             String requesterPrincipalId, String capabilityScopeJson, String resourceScopeJson,
                             String evidenceReference, String policyVersion, Status status,
                             String approverPrincipalId, String responseId, String reason,
                             Instant expiresAt, Instant createdAt, Instant decidedAt, long revision) {
    public ApprovalRecord {
        scopeId=required(scopeId,"scopeId"); approvalId=required(approvalId,"approvalId");
        binding=Objects.requireNonNull(binding,"binding"); requesterPrincipalId=required(requesterPrincipalId,"requesterPrincipalId");
        capabilityScopeJson=required(capabilityScopeJson,"capabilityScopeJson"); resourceScopeJson=required(resourceScopeJson,"resourceScopeJson");
        evidenceReference=required(evidenceReference,"evidenceReference"); policyVersion=required(policyVersion,"policyVersion");
        status=Objects.requireNonNull(status,"status"); approverPrincipalId=optional(approverPrincipalId); responseId=optional(responseId); reason=optional(reason);
        expiresAt=Objects.requireNonNull(expiresAt,"expiresAt"); createdAt=Objects.requireNonNull(createdAt,"createdAt"); decidedAt=decidedAt;
        if(!expiresAt.isAfter(createdAt) || revision<0) throw new IllegalArgumentException("invalid approval expiry or revision");
        if((status==Status.PENDING)!=(decidedAt==null)) throw new IllegalArgumentException("pending approval must not have a decision timestamp");
        if(status==Status.APPROVED && (approverPrincipalId.isBlank()||responseId.isBlank())) throw new IllegalArgumentException("approved record requires approver and response identity");
    }
    public enum Status { PENDING, APPROVED, DENIED, EXPIRED, REVOKED }
    private static String required(String v,String n){String s=Objects.requireNonNull(v,n).trim();if(s.isEmpty())throw new IllegalArgumentException(n+" cannot be blank");return s;}
    private static String optional(String v){return v==null?"":v.trim();}
}
