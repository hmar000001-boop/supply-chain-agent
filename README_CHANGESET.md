# Apply this change-only package

1. Extract this ZIP at the root of the existing `supply-chain-agent` repository.
2. Allow the six modified files to overwrite their existing counterparts.
3. Keep all other repository files, including the Maven bootstrap fix already applied.
4. Commit the changes and run the `Java CI` workflow.

This archive deliberately excludes the complete repository and all unchanged files.

The package does not claim a successful Maven build. Phase 0 remains open until CI passes the clean build, tests, PostgreSQL saver test and dependency-tree inspection.
