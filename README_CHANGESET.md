# Initial repository delivery — Milestone 0

This is the complete initial Java repository baseline for the Part III supply-chain application. It is not a change-only patch because no previous supply-chain application repository exists yet.

## Change inventory

- **Added:** all files in this initial repository, listed in `CHANGE_MANIFEST.csv` with SHA-256 hashes.
- **Modified:** none.
- **Deleted:** none.

Future milestones will use change-only ZIPs that include only added or modified files. Deleted paths will be recorded in each milestone manifest; unchanged repository files will not be repackaged.

## Verification statement

Static checks completed in the authoring environment: Maven POM XML parsing, shell syntax checks, and Java 21 compilation of the framework-neutral core module. The Maven Wrapper could not download its bootstrap JAR because DNS resolution for `repo.maven.apache.org` failed. Therefore the LangGraph4j/LangChain4j modules, Maven test suite and PostgreSQL integration test are **not build-verified** yet. The first GitHub Actions run is the required compatibility gate.
