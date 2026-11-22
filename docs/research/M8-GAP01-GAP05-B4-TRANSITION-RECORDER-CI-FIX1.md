# M8 B4 transition recorder — CI Fix 1

**Candidate:** `M8-GAP01-GAP05-MECHANISM-EXPERIMENT-HARNESS-V1-CI-FIX1`  
**Candidate code-tree SHA-256:** `8f64c3765be2e2eefe0ed1b00ff018526860e188ef6bb446a10dd1d60ef27236`  
**Hosted CI:** pending; the owner-provided 2026-11-22 run failed in the mechanisms module before tests or later modules could run.

## Defects corrected

1. `RecoveryIntentionService.update` invoked `transitionKind(before, after)` although no classifier existed. The new classifier verifies intention identity, a contiguous one-version transition and immutable objective fingerprint. It classifies actual method changes first, then transitions into `RECONSIDERATION_PENDING`, then active-plan-reference changes; all other valid lifecycle changes are `OTHER`.
2. The experiment recorder accepted only a kind and attempted to reconstruct the actor from the intention's human-readable `reason`. That is not reliable: lifecycle operations such as suspension and terminalization do not consistently encode actor identity in the reason. The recorder port and in-memory entry now carry the actor explicitly from the service operation. Objective-completion evidence uses the verifier identity from the satisfaction proof; host-authorized termination records the host-authorized actor supplied to the service.
3. `B4IntentionRunEvidence` now maps actor identity from the typed audit entry, not from prose. The compact record constructor also captures the normalized objective fingerprint in a final local before using it in a lambda, fixing a Java 21 effectively-final compilation error.
4. The M8 architecture/test inventory counts were one annotated test behind after adding the B4 transition-recorder regression test. The expected annotation count is now synchronized at 125.

## Behavioral boundaries preserved

- The committed objective fingerprint remains immutable across every recorded B4 transition.
- Repository compare-and-set and existing actor-authorization checks remain in place.
- Active-plan binding keeps its exact existing `reason` value, preserving checkpoint replay logic.
- The change does not authorize any planner action, resume an interrupted graph, dispatch an external action or activate a learning policy.

## Validation performed locally

- Java 21 production compilation: domain (53), memory (52), core (4), simulator (5), actions (58), mechanisms (166) — PASS.
- Java 21 compilation of the B1/B2/B3/B4/B6 typed mechanism evidence dependency closure — PASS.
- B4 transition/evidence smoke execution: classification and explicit actor attribution for reconsideration, method change, active-plan binding and suspension — PASS.
- `RecoveryIntentionServiceTest` source type-check using local JUnit API stubs — PASS; this is not a Surefire/JUnit execution.
- M8 architecture, R2, F-03/F-04, full-graph adapter, mechanism evidence and split-register guards — PASS.
- Full Maven reactor, Surefire and live PostgreSQL integration — NOT RUN locally. The project wrapper requires downloading Maven 3.9.16 from Apache; this environment has no usable Maven installation/cache.

The next required acceptance is the owner's hosted command:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean install
```

No empirical training, holdout or transfer experiment is claimed to have run.
