# Apply this change set

Extract this ZIP at the repository root, preserving relative paths. This is a change-only package, not a complete project archive.

The parent `pom.xml` imports the official SLF4J 2.0.20 BOM so transitive logging dependencies from LangGraph4j 1.8.20 and LangChain4j 1.23.0 converge on one managed version. Keep Maven Enforcer's `dependencyConvergence` rule enabled.

After applying, run:

```bash
./mvnw -B -ntp -Dphase0.postgres=true clean verify
./mvnw -B -ntp dependency:tree -Dincludes=org.slf4j -DoutputFile=target/slf4j-dependency-tree.txt
```

Do not treat dependency convergence as verified until CI passes and the dependency-tree artifact shows the aligned version.
