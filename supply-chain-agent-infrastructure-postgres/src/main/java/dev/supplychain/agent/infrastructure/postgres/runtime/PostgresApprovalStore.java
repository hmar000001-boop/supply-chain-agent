package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;
import dev.supplychain.agent.runtime.cognitive.lifecycle.CognitiveCycleAuthorityPort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Approval audit repository. Authentication is external; this store persists only trusted adapter identities/decisions. */
public final class PostgresApprovalStore {
    private final DataSource dataSource;
    public PostgresApprovalStore(DataSource dataSource){this.dataSource=Objects.requireNonNull(dataSource,"dataSource");}

    public ApprovalRecord create(String scopeId,String approvalId,CognitiveCycleAuthorityPort.ApprovalBinding binding,
                                 String requesterPrincipalId,String capabilityScopeJson,String resourceScopeJson,
                                 String evidenceReference,String policyVersion,Instant expiresAt,Instant createdAt){
        String scope=required(scopeId), id=required(approvalId);
        if(!scope.equals(binding.memoryScopeId())) throw new SecurityException("approval scope differs from action-plan binding");
        String caps=PostgresJsonArtifactRepository.validJson(capabilityScopeJson), resources=PostgresJsonArtifactRepository.validJson(resourceScopeJson);
        try(Connection c=dataSource.getConnection()){
            try(PreparedStatement s=c.prepareStatement("""
                    INSERT INTO runtime_approval(scope_id,approval_id,request_id,execution_id,candidate_set_id,candidate_id,
                        intention_id,intention_version,action_plan_fingerprint,requester_principal_id,capability_scope,resource_scope,
                        evidence_reference,policy_version,status,expires_at,created_at,revision)
                    VALUES(?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,'PENDING',?,?,0)
                    """)){
                s.setString(1,scope);s.setString(2,id);s.setString(3,binding.requestId());s.setString(4,binding.executionId());
                s.setString(5,binding.candidateSetId());s.setString(6,binding.candidateId());s.setString(7,binding.intentionId());
                s.setLong(8,binding.intentionVersion());s.setString(9,binding.actionPlanFingerprint());s.setString(10,required(requesterPrincipalId));
                s.setString(11,caps);s.setString(12,resources);s.setString(13,required(evidenceReference));s.setString(14,required(policyVersion));
                s.setObject(15,expiresAt);s.setObject(16,createdAt);s.executeUpdate();
            }
            return find(scope,id).orElseThrow();
        }catch(SQLException ex){if("23505".equals(ex.getSQLState()))throw new SecurityException("approval ID already exists",ex);throw new PostgresPersistenceException("PostgreSQL approval creation failed",ex);}
    }

    public Optional<ApprovalRecord> find(String scopeId,String approvalId){
        try(Connection c=dataSource.getConnection();PreparedStatement s=c.prepareStatement("""
                SELECT request_id,execution_id,candidate_set_id,candidate_id,intention_id,intention_version,action_plan_fingerprint,
                    requester_principal_id,capability_scope::text,resource_scope::text,evidence_reference,policy_version,status,
                    approver_principal_id,response_id,reason,expires_at,created_at,decided_at,revision
                FROM runtime_approval WHERE scope_id=? AND approval_id=?
                """)){
            s.setString(1,required(scopeId));s.setString(2,required(approvalId));
            try(ResultSet r=s.executeQuery()){if(!r.next())return Optional.empty();
                var binding=new CognitiveCycleAuthorityPort.ApprovalBinding(r.getString(1),r.getString(2),scopeId,
                        r.getString(3),r.getString(4),r.getString(5),r.getLong(6),r.getString(7).trim());
                var decided=r.getTimestamp(19);
                return Optional.of(new ApprovalRecord(scopeId,approvalId,binding,r.getString(8),r.getString(9),r.getString(10),
                        r.getString(11),r.getString(12),ApprovalRecord.Status.valueOf(r.getString(13)),r.getString(14),r.getString(15),
                        r.getString(16),r.getTimestamp(17).toInstant(),r.getTimestamp(18).toInstant(),decided==null?null:decided.toInstant(),r.getLong(20)));
            }
        }catch(SQLException ex){throw new PostgresPersistenceException("PostgreSQL approval lookup failed",ex);}
    }

    public ApprovalRecord decide(String scopeId,String approvalId,long expectedRevision,boolean approved,
                                  String approverPrincipalId,String responseId,String reason,Instant at){
        if(expectedRevision<0)throw new IllegalArgumentException("expectedRevision cannot be negative");
        return transaction(c->{
            ApprovalRecord current=lock(c,scopeId,approvalId).orElseThrow(()->new IllegalArgumentException("approval not found"));
            if(current.revision()!=expectedRevision)throw new IllegalStateException("approval revision conflict");
            if(current.status()!=ApprovalRecord.Status.PENDING)throw new IllegalStateException("approval is no longer pending");
            if(!current.expiresAt().isAfter(at))throw new IllegalStateException("approval has expired");
            String principal=required(approverPrincipalId), response=required(responseId);
            try(PreparedStatement s=c.prepareStatement("""
                    UPDATE runtime_approval SET status=?,approver_principal_id=?,response_id=?,reason=?,decided_at=?,revision=revision+1
                    WHERE scope_id=? AND approval_id=? AND status='PENDING' AND revision=? AND expires_at>?
                    """)){
                s.setString(1,approved?"APPROVED":"DENIED");s.setString(2,principal);s.setString(3,response);
                s.setString(4,optional(reason));s.setObject(5,at);s.setString(6,required(scopeId));s.setString(7,required(approvalId));
                s.setLong(8,expectedRevision);s.setObject(9,at);
                if(s.executeUpdate()!=1)throw new IllegalStateException("approval decision lost a race or expired");
            }
            return lock(c,scopeId,approvalId).orElseThrow();
        });
    }

    public boolean revoke(String scopeId,String approvalId,long expectedRevision,String actorId,String reason,Instant at){
        return transaction(c->{
            try(PreparedStatement s=c.prepareStatement("""
                    UPDATE runtime_approval SET status='REVOKED',approver_principal_id=?,reason=?,decided_at=?,revision=revision+1
                    WHERE scope_id=? AND approval_id=? AND revision=? AND status IN ('PENDING','APPROVED')
                    """)){
                s.setString(1,required(actorId));s.setString(2,required(reason));s.setObject(3,at);s.setString(4,required(scopeId));
                s.setString(5,required(approvalId));s.setLong(6,expectedRevision);return s.executeUpdate()==1;
            }
        });
    }

    /** Exact approval revalidation used at resume and immediately before external dispatch. */
    public boolean isCurrentlyValid(String scopeId,String approvalId,CognitiveCycleAuthorityPort.ApprovalBinding binding,Instant now){
        return transaction(c->{
            Optional<ApprovalRecord> value=lock(c,scopeId,approvalId);if(value.isEmpty())return false;
            ApprovalRecord record=value.get();
            if(record.status()==ApprovalRecord.Status.PENDING&&!record.expiresAt().isAfter(now)){
                try(PreparedStatement s=c.prepareStatement("UPDATE runtime_approval SET status='EXPIRED',reason='approval expiry reached',decided_at=?,revision=revision+1 WHERE scope_id=? AND approval_id=? AND status='PENDING' AND revision=?")){
                    s.setObject(1,now);s.setString(2,scopeId);s.setString(3,approvalId);s.setLong(4,record.revision());s.executeUpdate();
                }
                return false;
            }
            return record.status()==ApprovalRecord.Status.APPROVED && record.expiresAt().isAfter(now)
                    && record.binding().equals(binding);
        });
    }

    private Optional<ApprovalRecord> lock(Connection c,String scope,String id)throws SQLException{
        try(PreparedStatement s=c.prepareStatement("SELECT approval_id FROM runtime_approval WHERE scope_id=? AND approval_id=? FOR UPDATE")){
            s.setString(1,required(scope));s.setString(2,required(id));try(ResultSet r=s.executeQuery()){if(!r.next())return Optional.empty();}
        }
        // Same transaction/connection reads after the row lock.
        try(PreparedStatement s=c.prepareStatement("""
                SELECT request_id,execution_id,candidate_set_id,candidate_id,intention_id,intention_version,action_plan_fingerprint,
                    requester_principal_id,capability_scope::text,resource_scope::text,evidence_reference,policy_version,status,
                    approver_principal_id,response_id,reason,expires_at,created_at,decided_at,revision
                FROM runtime_approval WHERE scope_id=? AND approval_id=?
                """)){
            s.setString(1,scope);s.setString(2,id);try(ResultSet r=s.executeQuery()){if(!r.next())return Optional.empty();
                var binding=new CognitiveCycleAuthorityPort.ApprovalBinding(r.getString(1),r.getString(2),scope,r.getString(3),r.getString(4),r.getString(5),r.getLong(6),r.getString(7).trim());
                var d=r.getTimestamp(19);return Optional.of(new ApprovalRecord(scope,id,binding,r.getString(8),r.getString(9),r.getString(10),r.getString(11),r.getString(12),
                        ApprovalRecord.Status.valueOf(r.getString(13)),r.getString(14),r.getString(15),r.getString(16),r.getTimestamp(17).toInstant(),r.getTimestamp(18).toInstant(),d==null?null:d.toInstant(),r.getLong(20)));
            }
        }
    }
    @FunctionalInterface private interface Work<T>{T run(Connection c)throws SQLException;}
    private <T>T transaction(Work<T> work){try(Connection c=dataSource.getConnection()){c.setAutoCommit(false);try{T r=work.run(c);c.commit();return r;}catch(SQLException|RuntimeException ex){try{c.rollback();}catch(SQLException x){ex.addSuppressed(x);}if(ex instanceof SQLException sql)throw sql;throw ex;}}catch(SQLException ex){throw new PostgresPersistenceException("PostgreSQL approval transaction failed",ex);}}
    private static String required(String v){String s=Objects.requireNonNull(v,"value").trim();if(s.isEmpty())throw new IllegalArgumentException("value cannot be blank");return s;}
    private static String optional(String v){return v==null?"":v.trim();}
}
