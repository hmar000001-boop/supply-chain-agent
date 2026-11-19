package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.infrastructure.postgres.PostgresPersistenceException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Transactional outbox and deduplicating inbox. Delivery is at-least-once; consumers must be idempotent.
 * The connection-taking enqueue methods let a domain record and its outbox row commit in one transaction.
 */
public final class PostgresMessagingStore {
    private final DataSource dataSource;
    public PostgresMessagingStore(DataSource dataSource){this.dataSource=Objects.requireNonNull(dataSource,"dataSource");}

    public void enqueueOutbox(Connection transaction,String messageId,String aggregateId,String messageType,
                              String payloadJson,Instant createdAt){
        Objects.requireNonNull(transaction,"transaction");String payload=PostgresJsonArtifactRepository.validJson(payloadJson);
        try(PreparedStatement s=transaction.prepareStatement("""
                INSERT INTO outbox_message(message_id,aggregate_id,message_type,payload,state,attempts,next_attempt_at,created_at)
                VALUES(?,?,?,CAST(? AS jsonb),'PENDING',0,?,?)
                """)){
            s.setString(1,required(messageId));s.setString(2,required(aggregateId));s.setString(3,required(messageType));
            s.setString(4,payload);s.setObject(5,createdAt);s.setObject(6,createdAt);s.executeUpdate();
        }catch(SQLException ex){if("23505".equals(ex.getSQLState()))throw new SecurityException("outbox message ID already exists",ex);throw new PostgresPersistenceException("outbox enqueue failed",ex);}
    }

    public void enqueueOutbox(String messageId,String aggregateId,String messageType,String payloadJson,Instant createdAt){
        transaction(c->{enqueueOutbox(c,messageId,aggregateId,messageType,payloadJson,createdAt);return null;});
    }

    public List<OutboxMessage> claimOutbox(String workerId,Instant now,Duration lease,int limit){
        String worker=required(workerId);String leaseOwner=worker+":"+UUID.randomUUID();Objects.requireNonNull(now,"now");positive(lease,"lease");
        if(limit<1||limit>500)throw new IllegalArgumentException("outbox claim limit must be in [1,500]");
        return transaction(c->{
            try(PreparedStatement s=c.prepareStatement("""
                    WITH picked AS (
                        SELECT message_id FROM outbox_message
                        WHERE (state='PENDING' AND next_attempt_at<=clock_timestamp())
                           OR (state='IN_FLIGHT' AND lease_until<=clock_timestamp())
                        ORDER BY next_attempt_at,created_at,message_id
                        LIMIT ? FOR UPDATE SKIP LOCKED
                    )
                    UPDATE outbox_message m SET state='IN_FLIGHT',attempts=m.attempts+1,lease_owner=?,
                        lease_until=clock_timestamp() + (? * interval '1 millisecond')
                    FROM picked WHERE m.message_id=picked.message_id
                    RETURNING m.message_id,m.aggregate_id,m.message_type,m.payload::text,m.attempts,m.created_at,m.lease_until
                    """)){
                s.setInt(1,limit);s.setString(2,leaseOwner);s.setLong(3,lease.toMillis());
                try(ResultSet r=s.executeQuery()){
                    List<OutboxMessage> result=new ArrayList<>();while(r.next())result.add(new OutboxMessage(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5),r.getTimestamp(6).toInstant(),leaseOwner,r.getTimestamp(7).toInstant()));
                    return List.copyOf(result);
                }
            }
        });
    }

    public void acknowledgeOutbox(String messageId,String workerId,Instant at){
        Objects.requireNonNull(at,"at");
        transaction(c->{try(PreparedStatement s=c.prepareStatement("""
                UPDATE outbox_message SET state='PUBLISHED',published_at=clock_timestamp(),lease_owner=NULL,lease_until=NULL,last_error=NULL
                WHERE message_id=? AND state='IN_FLIGHT' AND lease_owner=? AND lease_until>clock_timestamp()
                """)){
            s.setString(1,required(messageId));s.setString(2,required(workerId));
            if(s.executeUpdate()!=1)throw new SecurityException("outbox acknowledgement rejected: lease is expired or owned by another publisher");
        }return null;});
    }

    public void failOutbox(String messageId,String workerId,Instant at,String error,int maxAttempts,
                           Duration initialBackoff,Duration maximumBackoff){
        Objects.requireNonNull(at,"at");
        if(maxAttempts<1)throw new IllegalArgumentException("maxAttempts must be positive");positive(initialBackoff,"initialBackoff");positive(maximumBackoff,"maximumBackoff");
        transaction(c->{
            int attempts;try(PreparedStatement q=c.prepareStatement("SELECT attempts FROM outbox_message WHERE message_id=? AND state='IN_FLIGHT' AND lease_owner=? AND lease_until>clock_timestamp() FOR UPDATE")){
                q.setString(1,required(messageId));q.setString(2,required(workerId));try(ResultSet r=q.executeQuery()){if(!r.next())throw new SecurityException("outbox failure rejected: message is not leased by this publisher");attempts=r.getInt(1);}}
            boolean dead=attempts>=maxAttempts;Duration delay=backoff(initialBackoff,maximumBackoff,attempts);
            try(PreparedStatement u=c.prepareStatement("""
                    UPDATE outbox_message SET state=?,next_attempt_at=clock_timestamp() + (? * interval '1 millisecond'),
                        lease_owner=NULL,lease_until=NULL,last_error=?
                    WHERE message_id=? AND state='IN_FLIGHT' AND lease_owner=? AND lease_until>clock_timestamp()
                    """)){
                u.setString(1,dead?"DEAD_LETTER":"PENDING");u.setLong(2,delay.toMillis());u.setString(3,truncate(error));
                u.setString(4,required(messageId));u.setString(5,required(workerId));if(u.executeUpdate()!=1)throw new IllegalStateException("outbox lease changed during failure recording");
            }return null;
        });
    }

    public InboxClaim beginInbox(String consumerId,String messageId,String payloadJson,String workerId,
                                 Instant now,Duration lease){
        String consumer=required(consumerId),id=required(messageId),worker=required(workerId),leaseOwner=worker+":"+UUID.randomUUID();
        String payload=PostgresJsonArtifactRepository.validJson(payloadJson),fingerprint=PostgresJsonArtifactRepository.sha256(payload);
        positive(lease,"lease");Objects.requireNonNull(now,"now");
        return transaction(c->{
            try(PreparedStatement insert=c.prepareStatement("""
                    INSERT INTO inbox_message(consumer_id,message_id,payload_fingerprint,payload,state,attempts,received_at,next_attempt_at,updated_at)
                    VALUES(?,?,?,CAST(? AS jsonb),'RECEIVED',0,?,?,?) ON CONFLICT(consumer_id,message_id) DO NOTHING
                    """)){
                insert.setString(1,consumer);insert.setString(2,id);insert.setString(3,fingerprint);insert.setString(4,payload);
                insert.setObject(5,now);insert.setObject(6,now);insert.setObject(7,now);insert.executeUpdate();
            }
            String state;String storedFingerprint;String storedPayload;int attempts;boolean leaseLive;boolean retryWait;
            try(PreparedStatement select=c.prepareStatement("""
                    SELECT payload_fingerprint,payload::text,state,attempts,lease_until,next_attempt_at,
                           (lease_until > clock_timestamp()) AS lease_live,
                           (next_attempt_at > clock_timestamp()) AS retry_wait
                    FROM inbox_message WHERE consumer_id=? AND message_id=? FOR UPDATE
                    """)){
                select.setString(1,consumer);select.setString(2,id);try(ResultSet r=select.executeQuery()){
                    if(!r.next())throw new IllegalStateException("inbox insert/select lost its row");
                    storedFingerprint=r.getString(1).trim();storedPayload=r.getString(2);state=r.getString(3);attempts=r.getInt(4);
                    leaseLive=r.getBoolean(7);retryWait=r.getBoolean(8);
                }
            }
            if(!storedFingerprint.equals(fingerprint))return new InboxClaim(InboxStatus.PAYLOAD_CONFLICT,id,storedPayload,attempts,"message ID was reused with a different payload");
            if("PROCESSED".equals(state))return new InboxClaim(InboxStatus.ALREADY_PROCESSED,id,storedPayload,attempts,"");
            if("DEAD_LETTER".equals(state))return new InboxClaim(InboxStatus.DEAD_LETTER,id,storedPayload,attempts,"message is quarantined");
            if("RECEIVED".equals(state)&&retryWait)return new InboxClaim(InboxStatus.IN_PROGRESS,id,storedPayload,attempts,"message retry backoff has not elapsed");
            if("PROCESSING".equals(state)&&leaseLive)return new InboxClaim(InboxStatus.IN_PROGRESS,id,storedPayload,attempts,"another worker owns the inbox lease");
            try(PreparedStatement update=c.prepareStatement("""
                    UPDATE inbox_message SET state='PROCESSING',attempts=attempts+1,claimed_by=?,
                        lease_until=clock_timestamp() + (? * interval '1 millisecond'),updated_at=clock_timestamp()
                    WHERE consumer_id=? AND message_id=? AND state IN ('RECEIVED','PROCESSING')
                    """)){
                update.setString(1,leaseOwner);update.setLong(2,lease.toMillis());update.setString(3,consumer);update.setString(4,id);
                if(update.executeUpdate()!=1)throw new IllegalStateException("inbox claim lost a lock-protected transition");
            }
            return new InboxClaim(InboxStatus.ACQUIRED,id,storedPayload,attempts+1,leaseOwner);
        });
    }

    public void acknowledgeInbox(String consumerId,String messageId,String workerId,Instant at){
        Objects.requireNonNull(at,"at");
        transaction(c->{try(PreparedStatement s=c.prepareStatement("""
                UPDATE inbox_message SET state='PROCESSED',claimed_by=NULL,lease_until=NULL,last_error=NULL,updated_at=clock_timestamp()
                WHERE consumer_id=? AND message_id=? AND state='PROCESSING' AND claimed_by=? AND lease_until>clock_timestamp()
                """)){
            s.setString(1,required(consumerId));s.setString(2,required(messageId));s.setString(3,required(workerId));
            if(s.executeUpdate()!=1)throw new SecurityException("inbox acknowledgement rejected: expired or foreign lease");
        }return null;});
    }

    public void failInbox(String consumerId,String messageId,String workerId,Instant at,String error,int maxAttempts,
                          Duration initialBackoff,Duration maximumBackoff){
        Objects.requireNonNull(at,"at");
        if(maxAttempts<1)throw new IllegalArgumentException("maxAttempts must be positive");positive(initialBackoff,"initialBackoff");positive(maximumBackoff,"maximumBackoff");
        transaction(c->{
            int attempts;try(PreparedStatement q=c.prepareStatement("SELECT attempts FROM inbox_message WHERE consumer_id=? AND message_id=? AND state='PROCESSING' AND claimed_by=? AND lease_until>clock_timestamp() FOR UPDATE")){
                q.setString(1,required(consumerId));q.setString(2,required(messageId));q.setString(3,required(workerId));
                try(ResultSet r=q.executeQuery()){if(!r.next())throw new SecurityException("inbox failure rejected: message is not leased by this worker");attempts=r.getInt(1);}}
            boolean dead=attempts>=maxAttempts;Duration delay=dead?Duration.ZERO:backoff(initialBackoff,maximumBackoff,attempts);
            try(PreparedStatement u=c.prepareStatement("""
                    UPDATE inbox_message SET state=?,claimed_by=NULL,lease_until=NULL,last_error=?,
                        next_attempt_at=clock_timestamp() + (? * interval '1 millisecond'),updated_at=clock_timestamp()
                    WHERE consumer_id=? AND message_id=? AND state='PROCESSING' AND claimed_by=? AND lease_until>clock_timestamp()
                    """)){
                u.setString(1,dead?"DEAD_LETTER":"RECEIVED");u.setString(2,truncate(error));u.setLong(3,delay.toMillis());
                u.setString(4,required(consumerId));u.setString(5,required(messageId));u.setString(6,required(workerId));
                if(u.executeUpdate()!=1)throw new IllegalStateException("inbox lease changed during failure recording");
            }return null;
        });
    }

    public <T>T inTransaction(TransactionWork<T> work){return transaction(c->work.run(c));}
    @FunctionalInterface public interface TransactionWork<T>{T run(Connection connection)throws SQLException;}
    public record OutboxMessage(String messageId,String aggregateId,String messageType,String payloadJson,int attempt,
                                Instant createdAt,String leaseOwner,Instant leaseUntil){ }
    public enum InboxStatus{ACQUIRED,ALREADY_PROCESSED,IN_PROGRESS,PAYLOAD_CONFLICT,DEAD_LETTER}
    /** For ACQUIRED, workerOrReason is a one-claim lease token; pass it to acknowledgeInbox/failInbox. */
    public record InboxClaim(InboxStatus status,String messageId,String payloadJson,int attempts,String workerOrReason){ }

    private interface Work<T>{T run(Connection c)throws SQLException;}
    private <T>T transaction(Work<T> work){try(Connection c=dataSource.getConnection()){c.setAutoCommit(false);try{T r=work.run(c);c.commit();return r;}
        catch(SQLException|RuntimeException ex){try{c.rollback();}catch(SQLException rollback){ex.addSuppressed(rollback);}if(ex instanceof SQLException sql)throw sql;throw ex;}}
        catch(SQLException ex){throw new PostgresPersistenceException("PostgreSQL inbox/outbox transaction failed",ex);}}
    private static Duration backoff(Duration initial,Duration max,int attempt){
        long multiplier=1L<<Math.min(30,Math.max(0,attempt-1));long millis;
        try{millis=Math.multiplyExact(initial.toMillis(),multiplier);}catch(ArithmeticException ex){millis=Long.MAX_VALUE;}
        return Duration.ofMillis(Math.min(millis,max.toMillis()));
    }
    private static void positive(Duration d,String name){if(d==null||d.isZero()||d.isNegative())throw new IllegalArgumentException(name+" must be positive");}
    private static String required(String v){String s=Objects.requireNonNull(v,"value").trim();if(s.isEmpty())throw new IllegalArgumentException("value cannot be blank");return s;}
    private static String truncate(String v){if(v==null)return "unspecified failure";String s=v.trim();return s.length()>2048?s.substring(0,2048):s;}
}
