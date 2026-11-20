M8 Pre-B7 / R1-C CI Fix 4

This ZIP contains the complete replacement Java source file, not a Git patch.

Repository-relative path:
supply-chain-agent-runtime/src/test/java/dev/supplychain/agent/runtime/cognitive/lifecycle/CognitiveCycleLifecycleServicesTest.java

Change:
Three MetaControlInputs constructor references in outer-class test methods are now fully qualified as
CognitiveCycleLifecyclePort.MetaControlInputs. The already-qualified references elsewhere are retained.
The unqualified references inside TestDomainPort are left unchanged because that class implements
CognitiveCycleLifecyclePort.

Apply by extracting the ZIP at the repository root and allowing the included file to overwrite the
existing file at the same path. No other source files are included.

This is a source replacement only. Maven/CI validation has not been run here.
