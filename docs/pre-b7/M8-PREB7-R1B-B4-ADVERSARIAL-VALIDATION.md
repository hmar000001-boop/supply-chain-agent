# M8-PreB7 R1-B — B4 adversarial lifecycle validation

**Status:** Test changes prepared; CI validation pending  
**Baseline:** M8-B6 plus R0 corrections and R1-A CoALA working-memory projection  
**Dependency:** Apply this incremental patch only after the R1-A patch has been applied and its GitHub Actions CI is green.

## 1. Purpose

R1-B strengthens application-boundary evidence for Fan & Lan's B4 mechanism. It does not add an adaptive method-selection policy and does not change the B4 domain model. The B4 mechanism implementation already provides persistent intention state, explicit reconsideration, method replacement only after reconsideration, immutable objective fingerprint/constraints, versioned compare-and-set updates, bounded delegation return, proof-bound completion and terminal-state restrictions.

The gap was narrower: lifecycle-composition tests did not exercise all these controls through `CognitiveCycleLifecycleServices.governIntention` and the trusted domain/authority ports.

## 2. Added lifecycle tests

- `b4LifecycleRejectsMethodReplacementBeforeExplicitReconsideration`: a host directive cannot replace the method while the intention remains ACTIVE; status, method and version remain unchanged.
- `b4LifecycleAcceptsMethodChangeOnlyAfterExplicitReconsiderationAndPreservesObjective`: an explicit reconsideration transition allows a later method replacement; objective ID, fingerprint and hard constraints are preserved and the version advances exactly once per transition.
- `b4LifecycleRejectsStaleIntentionBeforeConsultingTheDirectivePort`: a stale graph intention version is rejected before the host is asked for another directive; the concurrent authoritative state is preserved.
- `b4AuthorizedTerminationIsExplicitAndCannotMasqueradeAsObjectiveCompletion`: termination requires the trusted authority port, preserves the protected objective identity, sets terminal status and carries no objective-satisfaction proof. It is not reported as successful completion.

## 3. Authority boundary

Method directives originate from `CognitiveCycleLifecyclePort`, which is a trusted host/domain adapter boundary; they are not read directly from model output or graph state. The lifecycle service validates intention version and status, while `RecoveryIntentionService` validates the reconsideration state and protected objective fingerprint. Production adapters must still enforce the authenticated domain policy for selecting a replacement method and must bind the plan reference to an authoritative candidate. This patch does not claim that arbitrary method IDs are globally authorized by the core service.

Termination uses the separate `CognitiveCycleAuthorityPort.isTerminationAuthorized` check. An authorized termination is an abandonment/stop outcome, not objective completion; completion still requires an evidence-backed `ObjectiveSatisfactionProof`.

## 4. Validation state

The tests have been added to source but have not been executed in this authoring environment. The local Maven Wrapper cannot bootstrap Maven 3.9.16 because Apache distribution hosts do not resolve from the environment. CI must compile and run these tests. Do not infer test success from source inspection or packaging checks.

## 5. Scope and next steps

This is test-only executable change: no production Java source, POM, dependency, migration or CI workflow changes. It does not add a learned B4 method-selection policy. After R1-A is green, apply R1-B and run full CI. Then proceed to R1-C uncertainty contract and propagation.
