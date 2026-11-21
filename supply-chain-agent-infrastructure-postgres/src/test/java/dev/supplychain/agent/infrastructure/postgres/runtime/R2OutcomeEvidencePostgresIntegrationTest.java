package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.actions.api.ActionEffect;
import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;
import dev.supplychain.agent.infrastructure.postgres.migration.PostgresMigrationRunner;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureDraft;
import dev.supplychain.agent.mechanisms.b2.RecoveryProcedureStatus;
import dev.supplychain.agent.mechanisms.b2.RegisteredRecoveryActionReference;
import dev.supplychain.agent.mechanisms.b3.ConsumerReceiptStatus;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeEvidence;
import dev.supplychain.agent.mechanisms.b3.DownstreamOutcomeSignal;
import dev.supplychain.agent.mechanisms.b3.WorkspaceReceiptEvidence;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL persistence, scope isolation, CAS history and repository-replacement coverage for R2. */
@EnabledIfSystemProperty(named = "phase0.postgres", matches = "true")
class R2OutcomeEvidencePostgresIntegrationTest {
    private static DataSource dataSource;
    private static final Instant NOW = Instant.parse("2026-11-20T18:00:00Z");

    @BeforeAll static void initializeSchema() {
        dataSource = PostgresDataSourceFactory.create(PostgresDatabaseConfig.fromEnvironment(System.getenv()));
        assertEquals(11, new PostgresMigrationRunner(dataSource).migrate().totalMigrations());
    }

    @Test void b2DraftAndAppendOnlyVersionHistorySurviveRepositoryReplacement() throws Exception {
        String scopeValue = "r2-b2-" + UUID.randomUUID();
        var scope = new MemoryScopeId(scopeValue);
        String digest = "a".repeat(64);
        var draft = new RecoveryProcedureDraft("draft-" + UUID.randomUUID(), scope, "execution-r2", "impasse-r2",
                "parent-r2", 7, "b".repeat(64), "inventory.shortage",
                List.of(new RegisteredRecoveryActionReference("inventory.reserve", Capability.INVENTORY_RESERVE,
                        ActionEffect.DOMAIN_WRITE, digest)),
                List.of("inventory.available"), List.of("reservation.confirmed"), List.of("reservation.rejected"),
                List.of(new EvidenceReference("erp", "inventory-version-17")), "c".repeat(64), 0,
                RecoveryProcedureStatus.DRAFT, NOW, NOW, NOW.plusSeconds(600), null, null, null);

        var writer = new PostgresRecoveryProcedureDraftRepository(dataSource, scopeValue, draftCodec());
        writer.saveNew(draft);
        // A distinct repository and stateless codec restore the value from PostgreSQL JSONB.
        var reader = new PostgresRecoveryProcedureDraftRepository(dataSource, scopeValue, draftCodec());
        assertEquals(draft, reader.findById(draft.id()).orElseThrow());

        var approved = draft.approve("ops-lead", "change-r2-approval", NOW.plusSeconds(30));
        assertTrue(reader.compareAndSet(draft.id(), draft.version(), approved));
        var replacementReader = new PostgresRecoveryProcedureDraftRepository(dataSource, scopeValue, draftCodec());
        assertEquals(approved, replacementReader.findById(draft.id()).orElseThrow());
        assertTrue(count("SELECT count(*) FROM b2_recovery_procedure_draft_history WHERE scope_id=? AND draft_id=?",
                scopeValue, draft.id()) == 2L, "initial draft and approval must both be recorded in append-only history");

        var otherScope = new PostgresRecoveryProcedureDraftRepository(dataSource, "other-" + scopeValue, draftCodec());
        assertTrue(otherScope.findById(draft.id()).isEmpty(), "scope-qualified reads must not expose another tenant's draft");
        assertFalse(replacementReader.compareAndSet(draft.id(), 0, approved), "stale versions must not overwrite the approved draft");
    }

    @Test void b3ReceiptsAndVerifiedOutcomesSurviveRepositoryReplacementAndRemainAppendOnly() {
        String scopeValue = "r2-b3-" + UUID.randomUUID();
        var scope = new MemoryScopeId(scopeValue);
        String workspace = "workspace-" + UUID.randomUUID();
        String cycle = "cycle-r2-" + UUID.randomUUID();
        String receiptId = workspace + "#planner";
        String candidateFingerprint = "d".repeat(64);
        var receipt = new WorkspaceReceiptEvidence(receiptId, scope, cycle, workspace, "planner",
                ConsumerReceiptStatus.ACCEPTED, Map.of("candidate-a", candidateFingerprint), "e".repeat(64),
                "f".repeat(64), "b3-admission-v1", NOW);

        var writer = new PostgresB3OutcomeEvidenceRepository(dataSource, scopeValue, receiptCodec(), outcomeCodec());
        writer.appendReceipt(receipt);
        var replacement = new PostgresB3OutcomeEvidenceRepository(dataSource, scopeValue, receiptCodec(), outcomeCodec());
        assertEquals(receipt, replacement.findReceipt(scope, receiptId).orElseThrow());

        var outcome = new DownstreamOutcomeEvidence("outcome-" + UUID.randomUUID(), receiptId, scope, cycle,
                workspace, "candidate-a", candidateFingerprint, "planner", DownstreamOutcomeSignal.USEFUL,
                "erp-reconciliation-verifier-v1", List.of(new EvidenceReference("erp", "shipment-receipt-9")),
                "f".repeat(64), "b3-admission-v1", NOW.plusSeconds(30));
        replacement.appendOutcome(outcome);
        var afterReplacement = new PostgresB3OutcomeEvidenceRepository(dataSource, scopeValue, receiptCodec(), outcomeCodec());
        assertEquals(List.of(outcome), afterReplacement.findOutcomes(scope, 100));
        afterReplacement.appendOutcome(outcome); // exact idempotent replay does not duplicate evidence
        assertEquals(1, afterReplacement.findOutcomes(scope, 100).size());

        var conflicting = new DownstreamOutcomeEvidence(outcome.idempotencyKey(), receiptId, scope, cycle,
                workspace, "candidate-a", candidateFingerprint, "planner", DownstreamOutcomeSignal.HARMFUL,
                "erp-reconciliation-verifier-v1", List.of(new EvidenceReference("erp", "different-proof")),
                "f".repeat(64), "b3-admission-v1", NOW.plusSeconds(30));
        assertThrows(SecurityException.class, () -> afterReplacement.appendOutcome(conflicting),
                "idempotency-key reuse with different outcome evidence must fail closed");
        assertTrue(new PostgresB3OutcomeEvidenceRepository(dataSource, "other-" + scopeValue, receiptCodec(), outcomeCodec())
                .findReceipt(new MemoryScopeId("other-" + scopeValue), receiptId).isEmpty());
    }

    private static long count(String sql, String scope, String id) throws Exception {
        try (var c = dataSource.getConnection(); var q = c.prepareStatement(sql)) {
            q.setString(1, scope); q.setString(2, id);
            try (var row = q.executeQuery()) { assertTrue(row.next()); return row.getLong(1); }
        }
    }

    // These integration-test codecs are explicit, deterministic and allow-listed. They use a
    // compact binary field encoding wrapped in JSON only to avoid adding a production JSON library
    // to the infrastructure module; production hosts still supply their own typed JSON codecs.
    private interface Encoder<T> { void write(T value, DataOutputStream out) throws IOException; }
    private interface Decoder<T> { T read(DataInputStream in) throws IOException; }
    private static <T> JsonCodec<T> codec(Encoder<T> encoder, Decoder<T> decoder) {
        return new JsonCodec<>() {
            @Override public String encode(T value) {
                try {
                    var bytes = new ByteArrayOutputStream();
                    try (var out = new DataOutputStream(bytes)) { out.writeInt(1); encoder.write(value, out); }
                    return "{\"schema\":1,\"payload\":\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray()) + "\"}";
                } catch (IOException failure) { throw new IllegalStateException("test codec encode failed", failure); }
            }
            @Override public T decode(String json) {
                Matcher matcher = Pattern.compile("\\\"payload\\\"\\s*:\\s*\\\"([A-Za-z0-9_-]+)\\\"").matcher(json);
                if (!matcher.find()) throw new IllegalArgumentException("test JSON payload field missing");
                try (var in = new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(matcher.group(1))))) {
                    if (in.readInt() != 1) throw new IllegalArgumentException("unsupported test codec schema");
                    T value = decoder.read(in);
                    if (in.available() != 0) throw new IllegalArgumentException("trailing test codec payload data");
                    return value;
                } catch (IOException failure) { throw new IllegalArgumentException("test codec decode failed", failure); }
            }
        };
    }

    private static JsonCodec<RecoveryProcedureDraft> draftCodec() {
        return codec((v,o)->{
            write(o,v.id()); write(o,v.scopeId().value()); write(o,v.executionId()); write(o,v.impasseCaseId());
            write(o,v.parentRequestId()); o.writeLong(v.parentWorkingMemoryVersion()); write(o,v.objectiveFingerprint());
            write(o,v.conditionKey()); o.writeInt(v.actions().size());
            for(var a:v.actions()){write(o,a.actionId());write(o,a.requiredCapability().name());write(o,a.effect().name());write(o,a.descriptorFingerprint());}
            writeList(o,v.preconditionKeys());writeList(o,v.expectedPostconditionKeys());writeList(o,v.failureConditionKeys());
            writeEvidence(o,v.evidence());write(o,v.sourceFingerprint());o.writeLong(v.version());write(o,v.status().name());
            write(o,v.createdAt().toString());write(o,v.updatedAt().toString());write(o,v.expiresAt().toString());
            writeNullable(o,v.approvedBy());writeNullable(o,v.approvalReference());writeNullable(o,v.approvedAt()==null?null:v.approvedAt().toString());
        }, in->{
            String id=read(in),scope=read(in),execution=read(in),impasse=read(in),parent=read(in);long parentVersion=in.readLong();
            String objective=read(in),condition=read(in);int count=in.readInt();var actions=new ArrayList<RegisteredRecoveryActionReference>();
            for(int i=0;i<count;i++)actions.add(new RegisteredRecoveryActionReference(read(in),Capability.valueOf(read(in)),ActionEffect.valueOf(read(in)),read(in)));
            var pre=readList(in);var post=readList(in);var fail=readList(in);var evidence=readEvidence(in);String fingerprint=read(in);
            long version=in.readLong();var status=RecoveryProcedureStatus.valueOf(read(in));Instant created=Instant.parse(read(in));
            Instant updated=Instant.parse(read(in));Instant expires=Instant.parse(read(in));String by=readNullable(in),reference=readNullable(in);
            String approved=readNullable(in);
            return new RecoveryProcedureDraft(id,new MemoryScopeId(scope),execution,impasse,parent,parentVersion,objective,condition,
                    actions,pre,post,fail,evidence,fingerprint,version,status,created,updated,expires,by,reference,
                    approved==null?null:Instant.parse(approved));
        });
    }

    private static JsonCodec<WorkspaceReceiptEvidence> receiptCodec() {
        return codec((v,o)->{
            write(o,v.receiptId());write(o,v.scopeId().value());write(o,v.cycleId());write(o,v.workspaceId());write(o,v.consumerId());
            write(o,v.status().name());o.writeInt(v.consumedCandidateFingerprints().size());
            for(var e:new TreeMap<>(v.consumedCandidateFingerprints()).entrySet()){write(o,e.getKey());write(o,e.getValue());}
            write(o,v.workspaceFingerprint());write(o,v.experimentUnitFingerprint());write(o,v.admissionPolicyVersion());write(o,v.receivedAt().toString());
        },in->{
            String receipt=read(in),scope=read(in),cycle=read(in),workspace=read(in),consumer=read(in);
            var status=ConsumerReceiptStatus.valueOf(read(in));int size=in.readInt();var map=new TreeMap<String,String>();
            for(int i=0;i<size;i++)map.put(read(in),read(in));
            return new WorkspaceReceiptEvidence(receipt,new MemoryScopeId(scope),cycle,workspace,consumer,status,map,
                    read(in),read(in),read(in),Instant.parse(read(in)));
        });
    }

    private static JsonCodec<DownstreamOutcomeEvidence> outcomeCodec() {
        return codec((v,o)->{
            write(o,v.idempotencyKey());write(o,v.receiptId());write(o,v.scopeId().value());write(o,v.cycleId());
            write(o,v.workspaceId());write(o,v.candidateId());write(o,v.candidateFingerprint());write(o,v.consumerId());
            write(o,v.signal().name());write(o,v.verifierId());writeEvidence(o,v.evidence());write(o,v.experimentUnitFingerprint());write(o,v.admissionPolicyVersion());
            write(o,v.verifiedAt().toString());
        },in->new DownstreamOutcomeEvidence(read(in),read(in),new MemoryScopeId(read(in)),read(in),read(in),read(in),read(in),
                read(in),DownstreamOutcomeSignal.valueOf(read(in)),read(in),readEvidence(in),read(in),read(in),Instant.parse(read(in))));
    }

    private static void write(DataOutputStream out,String value) throws IOException { out.writeUTF(value); }
    private static String read(DataInputStream in) throws IOException { return in.readUTF(); }
    private static void writeNullable(DataOutputStream out,String value)throws IOException{out.writeBoolean(value!=null);if(value!=null)write(out,value);}
    private static String readNullable(DataInputStream in)throws IOException{return in.readBoolean()?read(in):null;}
    private static void writeList(DataOutputStream out,List<String> values)throws IOException{out.writeInt(values.size());for(String v:values)write(out,v);}
    private static List<String> readList(DataInputStream in)throws IOException{int n=in.readInt();var values=new ArrayList<String>();for(int i=0;i<n;i++)values.add(read(in));return List.copyOf(values);}
    private static void writeEvidence(DataOutputStream out,List<EvidenceReference> refs)throws IOException{out.writeInt(refs.size());for(var r:refs){write(out,r.evidenceType());write(out,r.evidenceId());}}
    private static List<EvidenceReference> readEvidence(DataInputStream in)throws IOException{int n=in.readInt();var refs=new ArrayList<EvidenceReference>();for(int i=0;i<n;i++)refs.add(new EvidenceReference(read(in),read(in)));return List.copyOf(refs);}
}
