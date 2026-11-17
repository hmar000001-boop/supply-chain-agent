# CI Maven Wrapper HTTP 429 remediation

## Modified

- `.mvn/wrapper/maven-wrapper.properties` — pin Apache Maven 3.9.16 distribution URLs and SHA-512; remove Maven Central wrapper/distribution bootstrap URLs.
- `mvnw` — download directly from Apache with retry/backoff, verify SHA-512, install atomically and cache.
- `mvnw.cmd` — delegate to the PowerShell bootstrapper.
- `.mvn/wrapper/bootstrap-maven.ps1` — Windows download, retry, checksum and cache implementation.
- `.github/workflows/ci.yml` — cache the Maven binary separately; capture dependency tree only after a successful build.
- `.gitignore` — remove obsolete ignored wrapper-JAR entry.
- `README.md` — document the new bootstrap behavior.
- `docs/phase0/PHASE0-STATUS.md` — record the exact 429 failure and remediation without claiming the build passed.

## Added

- `docs/phase0/MAVEN-BOOTSTRAP.md` — bootstrap design and limitation.
- `CHANGELOG_CI_MAVEN_429.md` — this change record.
- `README_CHANGESET.md` — application instructions.
- `CHANGE_MANIFEST.csv` — file inventory and checksums.

## Deleted

None.

## Verification status

The POSIX shell script passes `sh -n`. A synthetic local ZIP distribution test exercised download, SHA-512 verification, extraction, caching and argument forwarding. The GitHub Actions workflow YAML parses successfully. The PowerShell implementation was reviewed but could not be executed because PowerShell is unavailable in this environment. Network access to Apache distribution hosts and Maven Central is unavailable here, so the real Apache archive download and full Maven build were not executed. The expected SHA-512 was obtained from Apache's official distribution checksum endpoint. GitHub Actions must verify the real bootstrap and then continue to dependency resolution and PostgreSQL tests.
