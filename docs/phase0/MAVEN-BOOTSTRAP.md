# Maven bootstrap and HTTP 429 remediation

## Failure observed

The 2026-11-17 GitHub Actions logs failed before Maven startup with `curl: (22) The requested URL returned error: 429`. The previous custom wrapper attempted to download `maven-wrapper.jar` from Maven Central and the Maven binary itself was also configured on Maven Central.

## Change

The project now bootstraps the pinned Apache Maven 3.9.16 binary directly from Apache distribution infrastructure:

1. Primary: `archive.apache.org`.
2. Fallback: `downloads.apache.org`.
3. Retries transient download failures, including HTTP 429 and server errors.
4. Verifies the archive with the pinned SHA-512 before extraction.
5. Installs into `$MAVEN_USER_HOME/wrapper/dists/apache-maven-3.9.16` (default `~/.m2/...`).
6. Reuses the cached installation on subsequent runs.
7. Supports both POSIX (`mvnw`) and Windows (`mvnw.cmd`) entry points.

No Maven Wrapper JAR is downloaded. `distributionVersion`, `distributionUrl`, `distributionFallbackUrl` and `distributionSha512Sum` in `.mvn/wrapper/maven-wrapper.properties` are the single source of truth.

## CI behavior

`actions/setup-java` continues to cache project dependencies under the Maven repository. A separate `actions/cache` entry caches the pinned Maven binary. Dependency-tree capture runs only if the build step succeeds; reports are still uploaded with `if: always()`.

## Scope and limitation

This removes Maven Central from the Maven installation bootstrap only. Maven still uses configured repositories for project dependencies and plugins, so a later HTTP 429 during dependency resolution is a separate Maven repository issue and should be handled by repository mirror/proxy or retry policy.
