package dev.supplychain.agent.mechanisms.b2;

import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.actions.api.ActionRegistry;
import dev.supplychain.agent.memory.common.MemoryConflictException;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.common.ProvenanceSource;
import dev.supplychain.agent.memory.procedural.ProcedureKind;
import dev.supplychain.agent.memory.procedural.ProcedureStatus;
import dev.supplychain.agent.memory.procedural.ProcedureVersion;
import dev.supplychain.agent.memory.procedural.ProcedureVersionId;
import dev.supplychain.agent.memory.procedural.ProceduralMemoryService;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * Governed bridge from an approved B2 draft into CoALA procedural memory.
 * B2 approval only permits staging: independent procedural validation and activation remain required.
 * Retrieved procedures are returned as planning knowledge and are never dispatched here.
 */
public final class RecoveryProcedurePromotionService {
    private static final int MAX_STAGE_ATTEMPTS = 4;

    private final RecoveryProcedureDraftService drafts;
    private final ProceduralMemoryService proceduralMemory;
    private final RecoveryProcedureArtifactStore artifacts;
    private final ActionRegistry actions;
    private final RecoveryDraftEvidenceVerifier evidenceVerifier;

    public RecoveryProcedurePromotionService(RecoveryProcedureDraftService drafts,
            ProceduralMemoryService proceduralMemory, RecoveryProcedureArtifactStore artifacts,
            ActionRegistry actions, RecoveryDraftEvidenceVerifier evidenceVerifier) {
        this.drafts = Objects.requireNonNull(drafts, "drafts");
        this.proceduralMemory = Objects.requireNonNull(proceduralMemory, "proceduralMemory");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.evidenceVerifier = Objects.requireNonNull(evidenceVerifier, "evidenceVerifier");
    }

    /**
     * Stages an already-approved B2 draft as a CoALA procedural-memory DRAFT.
     * This method does not validate, activate or execute the procedure.
     */
    public ProcedureVersion stageApprovedDraft(String draftId, long expectedDraftVersion,
            MemoryScopeId requestedScope, Instant now) {
        Objects.requireNonNull(requestedScope, "requestedScope");
        Objects.requireNonNull(now, "now");
        RecoveryProcedureDraft draft = drafts.get(draftId);
        requireApprovedSource(draft, expectedDraftVersion, requestedScope, now);

        RecoveryProcedureArtifact artifact = RecoveryProcedureArtifact.from(draft);
        verifyEvidenceAndDescriptors(artifact, now);
        var artifactReference = artifacts.publish(artifact);
        String key = procedureKey(draft.conditionKey());

        for (int attempt = 0; attempt < MAX_STAGE_ATTEMPTS; attempt++) {
            var versions = proceduralMemory.versions(requestedScope, key);
            Optional<ProcedureVersion> alreadyPromoted = versions.stream()
                    .filter(v -> v.provenance().sourceId().equals(draft.id()))
                    .findFirst();
            if (alreadyPromoted.isPresent()) {
                ProcedureVersion existing = alreadyPromoted.get();
                if (!Objects.equals(existing.provenance().sourceVersion(), draft.sourceFingerprint())
                        || !existing.artifact().equals(artifactReference)
                        || existing.kind() != ProcedureKind.RUNBOOK) {
                    throw new SecurityException("B2 draft is already bound to a different procedural-memory artifact");
                }
                return existing;
            }
            ProcedureVersion latest = versions.stream().max(Comparator.comparingInt(ProcedureVersion::versionNumber)).orElse(null);
            int nextVersion = latest == null ? 1 : latest.versionNumber() + 1;
            var provenance = new Provenance(ProvenanceSource.HUMAN_VERIFIED, draft.id(),
                    draft.approvedAt(), now, draft.sourceFingerprint());
            ProcedureVersion staged = ProcedureVersion.draft(requestedScope, key, nextVersion,
                    ProcedureKind.RUNBOOK, "Approved B2 recovery procedure for condition " + draft.conditionKey(),
                    artifactReference, provenance, now, draft.approvedBy(), latest == null ? null : latest.id());
            try {
                return proceduralMemory.stage(staged);
            } catch (MemoryConflictException concurrentAppend) {
                // Another writer may have appended this key. Re-read and distinguish exact retry from a new version.
                if (attempt == MAX_STAGE_ATTEMPTS - 1) throw concurrentAppend;
            }
        }
        throw new IllegalStateException("recovery procedure could not be appended after bounded retries");
    }

    public ProcedureVersion validate(ProcedureVersionId id, long expectedRevision, String reviewer, Instant at) {
        ProcedureVersion current = proceduralMemory.load(Objects.requireNonNull(id, "id"));
        requireRecoveryRunbook(current);
        requireIndependentActor(reviewer, current.createdBy(), "procedural reviewer must be independent of the B2 draft approver");
        return proceduralMemory.validate(id, expectedRevision, reviewer, at);
    }

    public ProcedureVersion activate(ProcedureVersionId id, long expectedRevision, String approver, Instant at) {
        ProcedureVersion current = proceduralMemory.load(Objects.requireNonNull(id, "id"));
        requireRecoveryRunbook(current);
        if (current.status() != ProcedureStatus.VALIDATED) throw new IllegalStateException("recovery procedure must be separately validated before activation");
        requireIndependentActor(approver, current.createdBy(), "procedural approver must be independent of the B2 draft approver");
        requireIndependentActor(approver, current.validatedBy(), "procedural approver must be independent of the procedural reviewer");
        return proceduralMemory.activate(id, expectedRevision, approver, at);
    }

    public ProcedureVersion reject(ProcedureVersionId id, long expectedRevision, String reviewer, Instant at) {
        ProcedureVersion current = proceduralMemory.load(Objects.requireNonNull(id, "id"));
        requireRecoveryRunbook(current);
        requireIndependentActor(reviewer, current.createdBy(), "procedural reviewer must be independent of the B2 draft approver");
        return proceduralMemory.reject(id, expectedRevision, reviewer, at);
    }

    /**
     * Retrieves only an ACTIVE procedure for an exact scope and condition key, then revalidates
     * the immutable artifact, evidence and current action descriptors. No action is executed.
     */
    public Optional<ReusableRecoveryProcedure> retrieveActive(MemoryScopeId scope, String conditionKey, Instant at) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(at, "at");
        String key = procedureKey(conditionKey);
        Optional<ProcedureVersion> active = proceduralMemory.active(scope, key);
        if (active.isEmpty()) return Optional.empty();
        ProcedureVersion procedure = active.get();
        requireRecoveryRunbook(procedure);
        var artifact = artifacts.retrieve(procedure.artifact(), scope)
                .orElseThrow(() -> new IllegalStateException("active recovery procedure references a missing artifact"));
        if (!artifact.scopeId().equals(scope) || !artifact.conditionKey().equals(conditionKey)
                || !artifact.sourceDraftId().equals(procedure.provenance().sourceId())
                || !artifact.sourceFingerprint().equals(procedure.provenance().sourceVersion())
                || !artifact.approvedBy().equals(procedure.createdBy())) {
            throw new SecurityException("active recovery procedure metadata does not match its artifact and provenance");
        }
        verifyEvidenceAndDescriptors(artifact, at);
        return Optional.of(new ReusableRecoveryProcedure(procedure, artifact));
    }

    public static String procedureKey(String conditionKey) {
        String value = Objects.requireNonNull(conditionKey, "conditionKey").trim();
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,159}"))
            throw new IllegalArgumentException("conditionKey must be a registered opaque key");
        return "b2.recovery." + value;
    }

    private static void requireApprovedSource(RecoveryProcedureDraft draft, long expectedVersion,
            MemoryScopeId requestedScope, Instant now) {
        if (draft.version() != expectedVersion) throw new IllegalStateException("recovery draft version conflict");
        if (draft.status() != RecoveryProcedureStatus.APPROVED) throw new IllegalStateException("only an approved B2 draft can be promoted");
        if (!draft.scopeId().equals(requestedScope)) throw new SecurityException("recovery draft scope does not match promotion scope");
        if (now.isBefore(draft.approvedAt()) || !now.isBefore(draft.expiresAt()))
            throw new IllegalStateException("approved B2 draft is outside its promotion validity interval");
    }

    private void verifyEvidenceAndDescriptors(RecoveryProcedureArtifact artifact, Instant at) {
        if (!evidenceVerifier.verify(artifact.scopeId(), artifact.evidence(), at))
            throw new SecurityException("recovery procedure evidence is stale, missing or outside scope");
        for (RegisteredRecoveryActionReference reference : artifact.actions()) {
            var current = RecoveryProcedureActionReferences.current(actions, new ActionId(reference.actionId()));
            if (!current.equals(reference)) throw new SecurityException("registered recovery action changed after approval: " + reference.actionId());
        }
    }

    private static void requireIndependentActor(String actor, String other, String message) {
        String value = Objects.requireNonNull(actor, "actor").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("actor cannot be blank");
        if (value.equals(other)) throw new SecurityException(message);
    }

    private static void requireRecoveryRunbook(ProcedureVersion procedure) {
        if (procedure.kind() != ProcedureKind.RUNBOOK
                || !procedure.procedureKey().startsWith("b2.recovery.")
                || procedure.provenance().source() != ProvenanceSource.HUMAN_VERIFIED) {
            throw new SecurityException("procedural item is not a governed B2 recovery runbook");
        }
    }
}
