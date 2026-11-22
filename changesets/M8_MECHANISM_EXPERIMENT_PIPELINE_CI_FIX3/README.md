# M8 mechanism experiment pipeline — CI Fix 3

## Apply

Extract this ZIP at the repository root. Project files retain their exact root-relative paths. Package instructions and checksums live under `changesets/M8_MECHANISM_EXPERIMENT_PIPELINE_CI_FIX3/` and will not overwrite root project metadata.

Project delta: **2 complete file replacements, 2 additions and zero deletions**. No production Java source, Maven POM or migration is changed.

## CI failure correction

The owner-provided 2026-11-22 16:00 UTC Maven log shows all modules compile, but the full-graph matched-pair test fails because the test fixture uses a stateless lambda workspace consumer. The JVM may cache that lambda instance, so two ScenarioRuntime instances can hold the same object reference. The fixture now constructs an explicit new WorkspaceConsumer implementation per runtime; the regression test asserts separate consumer references. The adapter's strict isolation guard remains unchanged.

## Execution-track decision

The source already has framework-neutral `RequestInterpreter` and `ReasoningProvider` ports, LangChain4j adapters accepting `ChatModel`, a deterministic simulator-backed planning world, and an independent simulator replay verifier. Current app/evaluation composition supplies fixed or scripted model responses, not a live provider. LangChain4j core is present, but this repository does not include a provider-specific runtime module/configuration.

Do not build a second graph. Keep deterministic reasoning + deterministic simulator as the primary M8 mechanism research track. Add a separately versioned optional model-in-the-loop profile later, injecting a real ChatModel while retaining the same graph, mechanism code, fixtures and deterministic world simulator. See `docs/research/M8-MECHANISM-EXPERIMENT-PIPELINE-CI-FIX3.md` for the detailed audit.

## Validation limits

M8 architecture, R2, F-03/F-04, full-graph adapter, mechanism evidence and scenario register guards pass. A focused Java 21 smoke demonstrates stateless-lambda caching and fresh anonymous object identity. The full Maven/Surefire/PostgreSQL reactor has not been run locally; hosted CI is required. No experiment runs or empirical outcomes are claimed.

Candidate code-tree SHA-256: `e9a52eff5493c0af0c9dc1912dc7aa1300c9e5c6f03f2d128e1f2c9677ec2d10`
