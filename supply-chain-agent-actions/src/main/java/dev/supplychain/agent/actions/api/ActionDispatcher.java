package dev.supplychain.agent.actions.api;

import dev.supplychain.agent.domain.common.DomainViolation;
import dev.supplychain.agent.memory.common.MemoryConflictException;
import dev.supplychain.agent.memory.common.MemoryNotFoundException;
import dev.supplychain.agent.memory.common.MemoryVersionConflictException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Enforces type, capability/resource scope, preconditions and idempotency before invoking a handler. */
public final class ActionDispatcher {
    private final ActionRegistry registry;
    private final CapabilityAuthorizer authorizer;
    private final ActionExecutionLedger ledger;

    public ActionDispatcher(ActionRegistry registry, CapabilityAuthorizer authorizer, ActionExecutionLedger ledger) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    public <I, O> ActionResult<O> dispatch(ActionId id, Class<I> inputType, Class<O> outputType,
                                          ActionContext context, I input) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(inputType, "inputType");
        Objects.requireNonNull(outputType, "outputType");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(input, "input");
        ActionHandler<?, ?> raw;
        try { raw = registry.require(id); }
        catch (IllegalArgumentException ex) {
            return ActionResult.failed(failure(ActionFailureCode.UNKNOWN_ACTION, ex.getMessage(), false, false));
        }
        if (!raw.inputType().equals(inputType) || !inputType.isInstance(input)) {
            return ActionResult.failed(failure(ActionFailureCode.INPUT_TYPE_MISMATCH,
                    "Input type does not match registered action " + id, false, false));
        }
        if (!raw.outputType().equals(outputType)) {
            return ActionResult.failed(failure(ActionFailureCode.OUTPUT_TYPE_MISMATCH,
                    "Output type does not match registered action " + id, false, false));
        }
        return dispatchTyped(cast(raw), context, inputType.cast(input));
    }

    @SuppressWarnings("unchecked")
    private static <I, O> ActionHandler<I, O> cast(ActionHandler<?, ?> handler) {
        return (ActionHandler<I, O>) handler;
    }

    private <I, O> ActionResult<O> dispatchTyped(ActionHandler<I, O> handler, ActionContext context, I input) {
        ActionDescriptor descriptor = handler.descriptor();
        Set<ResourceReference> resources;
        try { resources = Set.copyOf(handler.resources(input)); }
        catch (RuntimeException ex) {
            return ActionResult.failed(failure(ActionFailureCode.INVALID_INPUT, safeMessage(ex), false, false));
        }
        if (!authorizer.isAuthorized(context.principal(), descriptor.requiredCapability(), resources)) {
            return ActionResult.rejected(failure(ActionFailureCode.AUTHORIZATION_DENIED,
                    "Principal lacks " + descriptor.requiredCapability() + " for the requested resource scope", false, false));
        }
        IdempotencyLedgerKey ledgerKey = null;
        String requestFingerprint = null;
        String operationId = UUID.randomUUID().toString();
        Instant startedAt = context.now();
        long fencingToken = 0L;
        if (descriptor.idempotencyRequired()) {
            if (context.idempotencyKey() == null) {
                return ActionResult.rejected(failure(ActionFailureCode.MISSING_IDEMPOTENCY_KEY,
                        "A state-changing action requires an idempotency key", false, false));
            }
            ledgerKey = new IdempotencyLedgerKey(context.principal().principalId(), descriptor.id(), context.idempotencyKey());
            try {
                requestFingerprint = fingerprint(handler, input);
                var existing = ledger.inspect(ledgerKey, requestFingerprint);
                if (existing.isPresent()) return fromClaim(existing.get());
            } catch (RuntimeException ex) {
                return ActionResult.failed(failure(ActionFailureCode.PERMANENT_FAILURE,
                        "Idempotency ledger inspection failed; action was not executed: " + safeMessage(ex), false, false));
            }
        }

        // The read-only ledger inspection happens before mutable preconditions so a completed
        // duplicate can be acknowledged even though its successful execution changed the state.
        PreconditionCheck precondition;
        try { precondition = Objects.requireNonNull(handler.precondition(context, input), "precondition result"); }
        catch (MemoryVersionConflictException | MemoryConflictException ex) {
            return ActionResult.rejected(failure(ActionFailureCode.CONFLICT, safeMessage(ex), false, false));
        } catch (MemoryNotFoundException ex) {
            return ActionResult.rejected(failure(ActionFailureCode.NOT_FOUND, safeMessage(ex), false, false));
        } catch (SecurityException ex) {
            return ActionResult.rejected(failure(ActionFailureCode.AUTHORIZATION_DENIED, safeMessage(ex), false, false));
        } catch (RuntimeException ex) {
            return ActionResult.rejected(failure(ActionFailureCode.PRECONDITION_FAILED, safeMessage(ex), false, false));
        }
        if (!precondition.allowed()) {
            return ActionResult.rejected(failure(ActionFailureCode.PRECONDITION_FAILED,
                    precondition.code() + ": " + precondition.reason(), false, false));
        }

        if (ledgerKey != null) {
            ActionLedgerClaim claim;
            try {
                if (ledger instanceof FencedActionExecutionLedger fenced) {
                    FencedActionExecutionLedger.FencedClaim acquired = fenced.claimFenced(ledgerKey, requestFingerprint, startedAt);
                    claim = acquired.claim();
                    fencingToken = acquired.fencingToken();
                } else {
                    claim = ledger.claim(ledgerKey, requestFingerprint, startedAt);
                }
            }
            catch (RuntimeException ex) {
                return ActionResult.failed(failure(ActionFailureCode.PERMANENT_FAILURE,
                        "Idempotency ledger could not acquire the operation: " + safeMessage(ex), false, false));
            }
            if (claim.status() != ActionLedgerClaim.Status.ACQUIRED) return fromClaim(claim);
            operationId = claim.operationId();
        }

        ActionContext executionContext = context.withOperationId(operationId);
        try {
            O value = Objects.requireNonNull(handler.execute(executionContext, input), "action returned null");
            ActionReceipt receipt = new ActionReceipt(descriptor.id(), operationId, context.principal().principalId(),
                    context.correlationId(), startedAt, context.now(), handler.resultReference(value),
                    descriptor.idempotencyRequired() ? java.util.Map.of("idempotent", "true") : java.util.Map.of());
            if (ledgerKey != null) {
                if (ledger instanceof FencedActionExecutionLedger fenced) fenced.completeFenced(ledgerKey, fencingToken, receipt);
                else ledger.complete(ledgerKey, receipt);
            }
            return ActionResult.success(value, receipt);
        } catch (ActionExecutionException ex) {
            if (ledgerKey != null) recordFailure(ledgerKey, ex.failure(), fencingToken);
            return ActionResult.failed(ex.failure());
        } catch (DomainViolation ex) {
            ActionFailure failure = mapDomainViolation(ex);
            if (ledgerKey != null) recordFailure(ledgerKey, failure, fencingToken);
            return ActionResult.failed(failure);
        } catch (MemoryVersionConflictException | MemoryConflictException ex) {
            ActionFailure failure = failure(ActionFailureCode.CONFLICT, safeMessage(ex), false, false);
            if (ledgerKey != null) recordFailure(ledgerKey, failure, fencingToken);
            return ActionResult.failed(failure);
        } catch (MemoryNotFoundException ex) {
            ActionFailure failure = failure(ActionFailureCode.NOT_FOUND, safeMessage(ex), false, false);
            if (ledgerKey != null) recordFailure(ledgerKey, failure, fencingToken);
            return ActionResult.failed(failure);
        } catch (SecurityException ex) {
            ActionFailure failure = failure(ActionFailureCode.AUTHORIZATION_DENIED, safeMessage(ex), false, false);
            if (ledgerKey != null) recordFailure(ledgerKey, failure, fencingToken);
            return ActionResult.rejected(failure);
        } catch (IllegalArgumentException ex) {
            ActionFailure failure = failure(ActionFailureCode.INVALID_INPUT, safeMessage(ex), false, false);
            if (ledgerKey != null) recordFailure(ledgerKey, failure, fencingToken);
            return ActionResult.failed(failure);
        } catch (RuntimeException ex) {
            boolean mutating = descriptor.effect() != ActionEffect.READ_ONLY;
            ActionFailure failure = mutating
                    ? failure(ActionFailureCode.UNKNOWN_OUTCOME,
                        "A state-changing action failed without a definitive outcome; reconcile before retrying", false, true)
                    : failure(ActionFailureCode.TRANSIENT_FAILURE, safeMessage(ex), true, false);
            if (ledgerKey != null) recordFailure(ledgerKey, failure, fencingToken);
            return ActionResult.failed(failure);
        }
    }

    private static <O> ActionResult<O> fromClaim(ActionLedgerClaim claim) {
        return switch (claim.status()) {
            case ACQUIRED -> throw new IllegalStateException("ACQUIRED claim must continue to execution");
            case COMPLETED -> ActionResult.alreadyCompleted(claim.receipt());
            case IN_PROGRESS -> ActionResult.inProgress(claim.failure());
            case KEY_REUSED -> ActionResult.rejected(claim.failure());
            case FAILED, UNKNOWN_OUTCOME -> ActionResult.failed(claim.failure());
        };
    }

    private static <I, O> String fingerprint(ActionHandler<I, O> handler, I input) {
        String canonical = Objects.requireNonNull(handler.requestFingerprint(input), "request fingerprint");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private void recordFailure(IdempotencyLedgerKey key, ActionFailure failure, long fencingToken) {
        if (ledger instanceof FencedActionExecutionLedger fenced) fenced.failFenced(key, fencingToken, failure);
        else ledger.fail(key, failure);
    }

    private static ActionFailure mapDomainViolation(DomainViolation ex) {
        return switch (ex.code()) {
            case NOT_FOUND -> failure(ActionFailureCode.NOT_FOUND, ex.getMessage(), false, false);
            case INSUFFICIENT_INVENTORY, CAPACITY_EXCEEDED, UNQUALIFIED_SUPPLIER,
                    UNQUALIFIED_SUBSTITUTE, ORDER_QUANTITY_EXCEEDED ->
                    failure(ActionFailureCode.CAPACITY_OR_QUANTITY_CONSTRAINT, ex.getMessage(), false, false);
            case CONFLICT, DUPLICATE_ID, INVALID_STATE, INVALID_TRANSITION ->
                    failure(ActionFailureCode.CONFLICT, ex.getMessage(), false, false);
            case DUPLICATE_SKU, INVALID_REFERENCE, INVALID_QUANTITY, INVALID_DATE ->
                    failure(ActionFailureCode.INVALID_INPUT, ex.getMessage(), false, false);
        };
    }
    private static ActionFailure failure(ActionFailureCode code, String message, boolean retryable, boolean unknown) {
        return new ActionFailure(code, message == null || message.isBlank() ? code.name() : message, retryable, unknown);
    }
    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
