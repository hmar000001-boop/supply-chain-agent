M8 Pre-B7 — R1-C CI Fix 5 (full source replacement)

Purpose
-------
Correct the graph-test fixture for the R1-C uncertainty refresh after host
human control. The production graph intentionally refreshes uncertainty and
re-enters B6 before authorization. The previous fixture returned
INTERRUPT_FOR_HUMAN on every B6 call, so the resumed cycle was interrupted
again instead of exercising the refreshed B6 decision.

Change
------
This delivery replaces one complete source file:

supply-chain-agent-runtime/src/test/java/dev/supplychain/agent/runtime/cognitive/CognitiveCycleGraphTest.java

The B6 human-control test now scripts the first B6 decision as
INTERRUPT_FOR_HUMAN and the post-human-control B6 decision as CONTINUE. It
asserts that B6 runs twice and that the second decision receives uncertainty
assessment revision 3 (post-retrieval v1, post-planning v2, post-human-control
v3). This keeps the production re-evaluation step intact; no production Java
source is changed.

Apply
-----
Extract this ZIP at the repository root, allowing the included file to replace
the existing file at the exact path above. No Git patch is included.

Validation note
---------------
The replacement was checked for the expected full source path, the two-stage
B6 action, the second-call count assertion, and the refreshed uncertainty
revision assertion. A local Maven run could not be completed in the build
container because the Maven wrapper distribution was unavailable offline.
Run the repository's normal CI command to validate the complete reactor:

./mvnw -B -ntp -Dphase0.postgres=true clean install
