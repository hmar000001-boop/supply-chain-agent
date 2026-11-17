# M1 change-only package — application instructions

This archive contains only files added or modified for M1. It is designed to be extracted at the root of the user's current repository, which must already contain the green M0 baseline.

## Apply

1. Extract the ZIP at the repository root, preserving the paths.
2. Review `M1_CHANGE_MANIFEST.csv`.
3. Commit the changes.
4. Run the full CI command:

   ```bash
   ./mvnw -B -ntp -Dphase0.postgres=true clean verify
   ```

5. Confirm that the seven M0 framework/checkpoint tests still pass and that the new domain and simulator JUnit tests execute under Surefire.

## Important

- Do not replace the repository with this archive; it is a delta, not a complete project.
- No Phase 0 test or application source is intentionally removed.
- The domain module has no LangGraph4j, LangChain4j, JDBC or PostgreSQL dependency.
- The simulator depends only on the domain module and has no external side effects.
- M1 is not complete until the full Maven/CI build passes.

See `CHANGELOG_M1.md` for the summary and `M1_CHANGE_MANIFEST.csv` for the complete Added/Modified/Deleted inventory and SHA-256 hashes.
