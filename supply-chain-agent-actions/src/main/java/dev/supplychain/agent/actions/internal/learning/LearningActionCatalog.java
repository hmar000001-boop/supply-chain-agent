package dev.supplychain.agent.actions.internal.learning;

import dev.supplychain.agent.actions.api.ActionCategory;
import dev.supplychain.agent.actions.api.ActionDescriptor;
import dev.supplychain.agent.actions.api.ActionEffect;
import dev.supplychain.agent.actions.api.ActionHandler;
import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.actions.api.ActionRegistry;
import dev.supplychain.agent.actions.api.Capability;
import dev.supplychain.agent.actions.api.ResourceReference;
import dev.supplychain.agent.actions.api.PreconditionCheck;
import dev.supplychain.agent.memory.common.EvidenceReference;
import dev.supplychain.agent.memory.common.EntityReference;
import dev.supplychain.agent.memory.common.MemoryScopeId;
import dev.supplychain.agent.memory.common.Provenance;
import dev.supplychain.agent.memory.episodic.Episode;
import dev.supplychain.agent.memory.episodic.EpisodeOutcome;
import dev.supplychain.agent.memory.episodic.EpisodeEvent;
import dev.supplychain.agent.memory.episodic.EpisodicMemoryService;
import dev.supplychain.agent.memory.procedural.ProcedureArtifactReference;
import dev.supplychain.agent.memory.procedural.ProcedureKind;
import dev.supplychain.agent.memory.procedural.ProcedureVersion;
import dev.supplychain.agent.memory.procedural.ProcedureVersionId;
import dev.supplychain.agent.memory.procedural.ProceduralMemoryService;
import dev.supplychain.agent.memory.semantic.FactValue;
import dev.supplychain.agent.memory.semantic.SemanticFact;
import dev.supplychain.agent.memory.semantic.SemanticFactId;
import dev.supplychain.agent.memory.semantic.SemanticMemoryService;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Versioned CoALA learning-action surface. Recording/staging is deliberately distinct from
 * verification, validation and activation. Review actions use separate capabilities whose grants
 * must be issued by trusted host code to independent reviewers; model output never supplies the
 * reviewer identity. Mechanism-policy training/activation remains a separate governed operator
 * service and is intentionally not exposed as an ordinary agent action.
 */
public final class LearningActionCatalog {
    public static final int CONTRACT_VERSION = 1;
    private static final String MEMORY_SCOPE_RESOURCE = "memory-scope";

    private LearningActionCatalog() { }

    public static void register(ActionRegistry registry, EpisodicMemoryService episodic,
                                SemanticMemoryService semantic, ProceduralMemoryService procedural,
                                MemoryScopeId boundScope) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(episodic, "episodic");
        Objects.requireNonNull(semantic, "semantic");
        Objects.requireNonNull(procedural, "procedural");
        Objects.requireNonNull(boundScope, "boundScope");
        registry.register(new RecordEpisode(episodic, boundScope));
        registry.register(new VerifyEpisode(episodic, boundScope));
        registry.register(new RejectEpisode(episodic, boundScope));
        registry.register(new ProposeSemanticFact(semantic, boundScope));
        registry.register(new ApproveSemanticFact(semantic, boundScope));
        registry.register(new RejectSemanticFact(semantic, boundScope));
        registry.register(new StageProcedure(procedural, boundScope));
        registry.register(new ValidateProcedure(procedural, boundScope));
        registry.register(new ActivateProcedure(procedural, boundScope));
        registry.register(new RejectProcedure(procedural, boundScope));
    }

    public record EpisodeDraft(int contractVersion, MemoryScopeId scopeId, String title, String summary,
                               EpisodeOutcome outcome, Instant startedAt, Instant completedAt,
                               List<EntityReference> entities, List<EpisodeEvent> events,
                               Provenance provenance, List<EvidenceReference> evidence) {
        public EpisodeDraft {
            version(contractVersion); scopeId = Objects.requireNonNull(scopeId, "scopeId");
            title = required(title, "title"); summary = required(summary, "summary");
            outcome = Objects.requireNonNull(outcome, "outcome");
            startedAt = Objects.requireNonNull(startedAt, "startedAt");
            completedAt = Objects.requireNonNull(completedAt, "completedAt");
            entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
            events = List.copyOf(Objects.requireNonNull(events, "events"));
            provenance = Objects.requireNonNull(provenance, "provenance");
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
            if (completedAt.isBefore(startedAt)) throw new IllegalArgumentException("completedAt precedes startedAt");
        }
    }

    public record EpisodeReview(int contractVersion, MemoryScopeId scopeId, EpisodeIdRef episode,
                                long expectedVersion, EvidenceReference reviewEvidence) {
        public EpisodeReview {
            version(contractVersion); scopeId = Objects.requireNonNull(scopeId, "scopeId");
            episode = Objects.requireNonNull(episode, "episode");
            if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion cannot be negative");
            reviewEvidence = Objects.requireNonNull(reviewEvidence, "reviewEvidence");
        }
    }
    public record EpisodeIdRef(String value) { public EpisodeIdRef { value = required(value, "episodeId"); } }

    public record SemanticProposal(int contractVersion, MemoryScopeId scopeId, EntityReference subject,
                                   String predicate, FactValue value, Instant validFrom, Instant validUntil,
                                   Provenance provenance) {
        public SemanticProposal {
            version(contractVersion); scopeId = Objects.requireNonNull(scopeId, "scopeId");
            subject = Objects.requireNonNull(subject, "subject"); predicate = required(predicate, "predicate");
            value = Objects.requireNonNull(value, "value"); validFrom = Objects.requireNonNull(validFrom, "validFrom");
            provenance = Objects.requireNonNull(provenance, "provenance");
            if (validUntil != null && !validUntil.isAfter(validFrom)) throw new IllegalArgumentException("validUntil must follow validFrom");
        }
    }
    public record SemanticReview(int contractVersion, MemoryScopeId scopeId, String factId,
                                 long expectedVersion, EvidenceReference reviewEvidence) {
        public SemanticReview {
            version(contractVersion); scopeId = Objects.requireNonNull(scopeId, "scopeId");
            factId = required(factId, "factId");
            if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion cannot be negative");
            reviewEvidence = Objects.requireNonNull(reviewEvidence, "reviewEvidence");
        }
    }

    public record ProcedureDraft(int contractVersion, MemoryScopeId scopeId, String procedureKey,
                                 int versionNumber, ProcedureKind kind, String description,
                                 URI artifactUri, String artifactSha256, Provenance provenance,
                                 ProcedureVersionId predecessor) {
        public ProcedureDraft {
            version(contractVersion); scopeId = Objects.requireNonNull(scopeId, "scopeId");
            procedureKey = required(procedureKey, "procedureKey");
            if (versionNumber < 1) throw new IllegalArgumentException("versionNumber must be positive");
            kind = Objects.requireNonNull(kind, "kind"); description = required(description, "description");
            artifactUri = Objects.requireNonNull(artifactUri, "artifactUri");
            artifactSha256 = required(artifactSha256, "artifactSha256");
            provenance = Objects.requireNonNull(provenance, "provenance");
        }
        public ProcedureArtifactReference artifact() { return new ProcedureArtifactReference(artifactUri, artifactSha256); }
    }
    public record ProcedureReview(int contractVersion, MemoryScopeId scopeId, String procedureId,
                                  long expectedLifecycleRevision) {
        public ProcedureReview {
            version(contractVersion); scopeId = Objects.requireNonNull(scopeId, "scopeId");
            procedureId = required(procedureId, "procedureId");
            if (expectedLifecycleRevision < 0) throw new IllegalArgumentException("expectedLifecycleRevision cannot be negative");
        }
    }

    private static ActionDescriptor descriptor(String id, String name, String description,
                                               Capability capability) {
        return new ActionDescriptor(new ActionId(id), name, description, ActionCategory.LEARNING,
                ActionEffect.MEMORY_WRITE, capability, true);
    }
    private static Set<ResourceReference> scope(MemoryScopeId scope) {
        return Set.of(new ResourceReference(MEMORY_SCOPE_RESOURCE, scope.value()));
    }
    private static void version(int value) {
        if (value != CONTRACT_VERSION) throw new IllegalArgumentException("unsupported learning-action contract version: " + value);
    }
    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }

    private static final class RecordEpisode implements ActionHandler<EpisodeDraft, Episode> {
        private final EpisodicMemoryService service;
        private final MemoryScopeId boundScope;
        private final ActionDescriptor descriptor = LearningActionCatalog.descriptor("memory.episodic.record.v1", "Record episodic experience",
                "Record an unverified episodic experience; recording never makes it trusted knowledge.", Capability.MEMORY_RECORD_EPISODE);
        private RecordEpisode(EpisodicMemoryService service, MemoryScopeId boundScope) { this.service = service; this.boundScope = boundScope; }
        @Override public ActionDescriptor descriptor() { return descriptor; }
        @Override public Class<EpisodeDraft> inputType() { return EpisodeDraft.class; }
        @Override public Class<Episode> outputType() { return Episode.class; }
        @Override public Set<ResourceReference> resources(EpisodeDraft input) { return scope(input.scopeId()); }
        @Override public PreconditionCheck precondition(dev.supplychain.agent.actions.api.ActionContext context, EpisodeDraft input) { return scopePrecondition(boundScope, input.scopeId()); }
        @Override public Episode execute(dev.supplychain.agent.actions.api.ActionContext context, EpisodeDraft input) {
            EvidenceReference idempotency = new EvidenceReference("m3-idempotency", descriptor.id().value()
                    + ":" + context.principal().principalId() + ":" + context.idempotencyKey());
            List<EvidenceReference> suppliedEvidence = java.util.stream.Stream.concat(input.evidence().stream(),
                    input.events().stream().flatMap(event -> event.evidence().stream())).distinct().toList();
            if (input.provenance().isModelDerived() && suppliedEvidence.isEmpty()) {
                throw new IllegalArgumentException("model-derived episodic learning requires evidence supplied with the learning action");
            }
            return service.recordIdempotent(input.scopeId(), input.title(), input.summary(), input.outcome(),
                    input.startedAt(), input.completedAt(), context.now(), input.entities(), input.events(), input.evidence(),
                    new Provenance(input.provenance().source(), input.provenance().sourceId(),
                            input.provenance().observedAt(), context.now(), input.provenance().sourceVersion()), idempotency);
        }
        @Override public String resultReference(Episode output) { return output.id().value() + "@" + output.version(); }
    }

    private static final class VerifyEpisode implements ActionHandler<EpisodeReview, Episode> {
        private final EpisodicMemoryService service;
        private final MemoryScopeId boundScope;
        private final ActionDescriptor descriptor = LearningActionCatalog.descriptor("memory.episodic.verify.v1", "Verify episodic experience",
                "Independently verify a recorded episode using review evidence; requires a reviewer capability.", Capability.MEMORY_VERIFY_EPISODE);
        private VerifyEpisode(EpisodicMemoryService service, MemoryScopeId boundScope) { this.service = service; this.boundScope = boundScope; }
        @Override public ActionDescriptor descriptor() { return descriptor; }
        @Override public Class<EpisodeReview> inputType() { return EpisodeReview.class; }
        @Override public Class<Episode> outputType() { return Episode.class; }
        @Override public Set<ResourceReference> resources(EpisodeReview input) { return scope(input.scopeId()); }
        @Override public PreconditionCheck precondition(dev.supplychain.agent.actions.api.ActionContext context, EpisodeReview input) { return scopePrecondition(boundScope, input.scopeId()); }
        @Override public Episode execute(dev.supplychain.agent.actions.api.ActionContext context, EpisodeReview input) {
            var current = service.load(new dev.supplychain.agent.memory.episodic.EpisodeId(input.episode().value()));
            requireScope(input.scopeId(), current.scopeId());
            return service.verify(current.id(), input.expectedVersion(), context.principal().principalId(), input.reviewEvidence(), context.now());
        }
        @Override public String resultReference(Episode output) { return output.id().value() + "@" + output.version(); }
    }

    private static final class RejectEpisode implements ActionHandler<EpisodeReview, Episode> {
        private final EpisodicMemoryService service;
        private final MemoryScopeId boundScope;
        private final ActionDescriptor descriptor = LearningActionCatalog.descriptor("memory.episodic.reject.v1", "Reject episodic experience",
                "Reject a recorded episode with an independent reviewer and evidence.", Capability.MEMORY_REJECT_EPISODE);
        private RejectEpisode(EpisodicMemoryService service, MemoryScopeId boundScope) { this.service = service; this.boundScope = boundScope; }
        @Override public ActionDescriptor descriptor() { return descriptor; }
        @Override public Class<EpisodeReview> inputType() { return EpisodeReview.class; }
        @Override public Class<Episode> outputType() { return Episode.class; }
        @Override public Set<ResourceReference> resources(EpisodeReview input) { return scope(input.scopeId()); }
        @Override public PreconditionCheck precondition(dev.supplychain.agent.actions.api.ActionContext context, EpisodeReview input) { return scopePrecondition(boundScope, input.scopeId()); }
        @Override public Episode execute(dev.supplychain.agent.actions.api.ActionContext context, EpisodeReview input) {
            var current = service.load(new dev.supplychain.agent.memory.episodic.EpisodeId(input.episode().value()));
            requireScope(input.scopeId(), current.scopeId());
            return service.reject(current.id(), input.expectedVersion(), context.principal().principalId(), input.reviewEvidence(), context.now());
        }
        @Override public String resultReference(Episode output) { return output.id().value() + "@" + output.version(); }
    }

    private static final class ProposeSemanticFact implements ActionHandler<SemanticProposal, SemanticFact> {
        private final SemanticMemoryService service;
        private final MemoryScopeId boundScope;
        private final ActionDescriptor descriptor = LearningActionCatalog.descriptor("memory.semantic.propose.v1", "Propose semantic fact",
                "Stage a versioned semantic fact as PROPOSED; it is not available to trusted retrieval until reviewed and activated.", Capability.MEMORY_PROPOSE_SEMANTIC);
        private ProposeSemanticFact(SemanticMemoryService service, MemoryScopeId boundScope) { this.service = service; this.boundScope = boundScope; }
        @Override public ActionDescriptor descriptor() { return descriptor; }
        @Override public Class<SemanticProposal> inputType() { return SemanticProposal.class; }
        @Override public Class<SemanticFact> outputType() { return SemanticFact.class; }
        @Override public Set<ResourceReference> resources(SemanticProposal input) { return scope(input.scopeId()); }
        @Override public PreconditionCheck precondition(dev.supplychain.agent.actions.api.ActionContext context, SemanticProposal input) { return scopePrecondition(boundScope, input.scopeId()); }
        @Override public SemanticFact execute(dev.supplychain.agent.actions.api.ActionContext context, SemanticProposal input) {
            var fact = SemanticFact.proposed(new dev.supplychain.agent.memory.semantic.FactKey(input.scopeId(), input.subject(), input.predicate()),
                    input.value(), input.validFrom(), input.validUntil(), context.now(),
                    new Provenance(input.provenance().source(), input.provenance().sourceId(), input.provenance().observedAt(), context.now(), input.provenance().sourceVersion()));
            return service.propose(fact);
        }
        @Override public String resultReference(SemanticFact output) { return output.id().value() + "@" + output.version(); }
    }

    private static final class ApproveSemanticFact implements ActionHandler<SemanticReview, SemanticFact> {
        private final SemanticMemoryService service;
        private final MemoryScopeId boundScope;
        private final ActionDescriptor descriptor = LearningActionCatalog.descriptor("memory.semantic.approve.v1", "Approve semantic fact",
                "Approve and activate a proposed semantic fact with independent review evidence.", Capability.MEMORY_REVIEW_SEMANTIC);
        private ApproveSemanticFact(SemanticMemoryService service, MemoryScopeId boundScope) { this.service = service; this.boundScope = boundScope; }
        @Override public ActionDescriptor descriptor() { return descriptor; }
        @Override public Class<SemanticReview> inputType() { return SemanticReview.class; }
        @Override public Class<SemanticFact> outputType() { return SemanticFact.class; }
        @Override public Set<ResourceReference> resources(SemanticReview input) { return scope(input.scopeId()); }
        @Override public PreconditionCheck precondition(dev.supplychain.agent.actions.api.ActionContext context, SemanticReview input) { return scopePrecondition(boundScope, input.scopeId()); }
        @Override public SemanticFact execute(dev.supplychain.agent.actions.api.ActionContext context, SemanticReview input) {
            var id = new SemanticFactId(input.factId()); var current = service.load(id); requireScope(input.scopeId(), current.scopeId());
            return service.approveAndActivate(id, input.expectedVersion(), context.principal().principalId(), input.reviewEvidence(), context.now());
        }
        @Override public String resultReference(SemanticFact output) { return output.id().value() + "@" + output.version(); }
    }

    private static final class RejectSemanticFact implements ActionHandler<SemanticReview, SemanticFact> {
        private final SemanticMemoryService service;
        private final MemoryScopeId boundScope;
        private final ActionDescriptor descriptor = LearningActionCatalog.descriptor("memory.semantic.reject.v1", "Reject semantic fact",
                "Reject a proposed semantic fact with independent review evidence.", Capability.MEMORY_REVIEW_SEMANTIC);
        private RejectSemanticFact(SemanticMemoryService service, MemoryScopeId boundScope) { this.service = service; this.boundScope = boundScope; }
        @Override public ActionDescriptor descriptor() { return descriptor; }
        @Override public Class<SemanticReview> inputType() { return SemanticReview.class; }
        @Override public Class<SemanticFact> outputType() { return SemanticFact.class; }
        @Override public Set<ResourceReference> resources(SemanticReview input) { return scope(input.scopeId()); }
        @Override public PreconditionCheck precondition(dev.supplychain.agent.actions.api.ActionContext context, SemanticReview input) { return scopePrecondition(boundScope, input.scopeId()); }
        @Override public SemanticFact execute(dev.supplychain.agent.actions.api.ActionContext context, SemanticReview input) {
            var id = new SemanticFactId(input.factId()); var current = service.load(id); requireScope(input.scopeId(), current.scopeId());
            return service.reject(id, input.expectedVersion(), context.principal().principalId(), input.reviewEvidence(), context.now());
        }
        @Override public String resultReference(SemanticFact output) { return output.id().value() + "@" + output.version(); }
    }

    private static final class StageProcedure implements ActionHandler<ProcedureDraft, ProcedureVersion> {
        private final ProceduralMemoryService service;
        private final MemoryScopeId boundScope;
        private final ActionDescriptor descriptor = LearningActionCatalog.descriptor("memory.procedural.stage.v1", "Stage procedural memory",
                "Stage a content-addressed, immutable procedure as DRAFT; staging never authorizes execution.", Capability.MEMORY_STAGE_PROCEDURE);
        private StageProcedure(ProceduralMemoryService service, MemoryScopeId boundScope) { this.service = service; this.boundScope = boundScope; }
        @Override public ActionDescriptor descriptor() { return descriptor; }
        @Override public Class<ProcedureDraft> inputType() { return ProcedureDraft.class; }
        @Override public Class<ProcedureVersion> outputType() { return ProcedureVersion.class; }
        @Override public Set<ResourceReference> resources(ProcedureDraft input) { return scope(input.scopeId()); }
        @Override public PreconditionCheck precondition(dev.supplychain.agent.actions.api.ActionContext context, ProcedureDraft input) { return scopePrecondition(boundScope, input.scopeId()); }
        @Override public ProcedureVersion execute(dev.supplychain.agent.actions.api.ActionContext context, ProcedureDraft input) {
            Provenance provenance = new Provenance(input.provenance().source(), input.provenance().sourceId(),
                    input.provenance().observedAt(), context.now(), input.provenance().sourceVersion());
            return service.stage(input.scopeId(), input.procedureKey(), input.versionNumber(), input.kind(),
                    input.description(), input.artifact(), provenance, context.now(), context.principal().principalId(), input.predecessor());
        }
        @Override public String resultReference(ProcedureVersion output) { return output.id().value() + "@r" + output.lifecycleRevision(); }
    }

    private static final class ValidateProcedure implements ActionHandler<ProcedureReview, ProcedureVersion> {
        private final ProceduralMemoryService service;
        private final MemoryScopeId boundScope;
        private final ActionDescriptor descriptor = LearningActionCatalog.descriptor("memory.procedural.validate.v1", "Validate procedural memory",
                "Independently validate a staged procedure; validation is separate from activation.", Capability.MEMORY_REVIEW_PROCEDURE);
        private ValidateProcedure(ProceduralMemoryService service, MemoryScopeId boundScope) { this.service = service; this.boundScope = boundScope; }
        @Override public ActionDescriptor descriptor() { return descriptor; }
        @Override public Class<ProcedureReview> inputType() { return ProcedureReview.class; }
        @Override public Class<ProcedureVersion> outputType() { return ProcedureVersion.class; }
        @Override public Set<ResourceReference> resources(ProcedureReview input) { return scope(input.scopeId()); }
        @Override public PreconditionCheck precondition(dev.supplychain.agent.actions.api.ActionContext context, ProcedureReview input) { return scopePrecondition(boundScope, input.scopeId()); }
        @Override public ProcedureVersion execute(dev.supplychain.agent.actions.api.ActionContext context, ProcedureReview input) {
            var id = new ProcedureVersionId(input.procedureId()); var current = service.load(id); requireScope(input.scopeId(), current.scopeId());
            requireIndependentReviewer(context.principal().principalId(), current.createdBy(), "procedure validator must differ from author");
            return service.validate(id, input.expectedLifecycleRevision(), context.principal().principalId(), context.now());
        }
        @Override public String resultReference(ProcedureVersion output) { return output.id().value() + "@r" + output.lifecycleRevision(); }
    }

    private static final class ActivateProcedure implements ActionHandler<ProcedureReview, ProcedureVersion> {
        private final ProceduralMemoryService service;
        private final MemoryScopeId boundScope;
        private final ActionDescriptor descriptor = LearningActionCatalog.descriptor("memory.procedural.activate.v1", "Activate procedural memory",
                "Activate an independently validated procedure; activation does not grant an action capability.", Capability.MEMORY_ACTIVATE_PROCEDURE);
        private ActivateProcedure(ProceduralMemoryService service, MemoryScopeId boundScope) { this.service = service; this.boundScope = boundScope; }
        @Override public ActionDescriptor descriptor() { return descriptor; }
        @Override public Class<ProcedureReview> inputType() { return ProcedureReview.class; }
        @Override public Class<ProcedureVersion> outputType() { return ProcedureVersion.class; }
        @Override public Set<ResourceReference> resources(ProcedureReview input) { return scope(input.scopeId()); }
        @Override public PreconditionCheck precondition(dev.supplychain.agent.actions.api.ActionContext context, ProcedureReview input) { return scopePrecondition(boundScope, input.scopeId()); }
        @Override public ProcedureVersion execute(dev.supplychain.agent.actions.api.ActionContext context, ProcedureReview input) {
            var id = new ProcedureVersionId(input.procedureId()); var current = service.load(id); requireScope(input.scopeId(), current.scopeId());
            if (current.validatedBy() == null) throw new SecurityException("procedure must be independently validated first");
            requireIndependentReviewer(context.principal().principalId(), current.createdBy(), "procedure approver must differ from author");
            requireIndependentReviewer(context.principal().principalId(), current.validatedBy(), "procedure approver must differ from validator");
            return service.activate(id, input.expectedLifecycleRevision(), context.principal().principalId(), context.now());
        }
        @Override public String resultReference(ProcedureVersion output) { return output.id().value() + "@r" + output.lifecycleRevision(); }
    }

    private static final class RejectProcedure implements ActionHandler<ProcedureReview, ProcedureVersion> {
        private final ProceduralMemoryService service;
        private final MemoryScopeId boundScope;
        private final ActionDescriptor descriptor = LearningActionCatalog.descriptor("memory.procedural.reject.v1", "Reject procedural memory",
                "Reject a staged procedure through an independent reviewer.", Capability.MEMORY_REVIEW_PROCEDURE);
        private RejectProcedure(ProceduralMemoryService service, MemoryScopeId boundScope) { this.service = service; this.boundScope = boundScope; }
        @Override public ActionDescriptor descriptor() { return descriptor; }
        @Override public Class<ProcedureReview> inputType() { return ProcedureReview.class; }
        @Override public Class<ProcedureVersion> outputType() { return ProcedureVersion.class; }
        @Override public Set<ResourceReference> resources(ProcedureReview input) { return scope(input.scopeId()); }
        @Override public PreconditionCheck precondition(dev.supplychain.agent.actions.api.ActionContext context, ProcedureReview input) { return scopePrecondition(boundScope, input.scopeId()); }
        @Override public ProcedureVersion execute(dev.supplychain.agent.actions.api.ActionContext context, ProcedureReview input) {
            var id = new ProcedureVersionId(input.procedureId()); var current = service.load(id); requireScope(input.scopeId(), current.scopeId());
            requireIndependentReviewer(context.principal().principalId(), current.createdBy(), "procedure reviewer must differ from author");
            return service.reject(id, input.expectedLifecycleRevision(), context.principal().principalId(), context.now());
        }
        @Override public String resultReference(ProcedureVersion output) { return output.id().value() + "@r" + output.lifecycleRevision(); }
    }

    private static PreconditionCheck scopePrecondition(MemoryScopeId bound, MemoryScopeId requested) {
        return bound.equals(requested) ? PreconditionCheck.pass()
                : PreconditionCheck.reject("MEMORY_SCOPE_MISMATCH", "learning action is outside the host-bound memory scope");
    }

    private static void requireScope(MemoryScopeId requested, MemoryScopeId actual) {
        if (!requested.equals(actual)) throw new SecurityException("learning operation attempted outside its memory scope");
    }
    private static void requireIndependentReviewer(String reviewer, String author, String message) {
        if (Objects.equals(reviewer, author)) throw new SecurityException(message);
    }
}
