# PostgreSQL saver builder exception contract — 2026-11-17

`PostgresCheckpointCompatibilityTest.newSaver(...)` calls `PostgresSaver.builder()...build()`. With the project's resolved LangGraph4j PostgreSQL saver 1.8.20 artifact, the builder's `build()` method declares the checked `java.sql.SQLException`.

The helper now declares `throws SQLException`. The test method already propagates checked exceptions to JUnit, so connection failures and schema initialization failures remain visible test failures. No exception is caught or ignored.

This is a test-compilation correction only. It does not assert that the PostgreSQL checkpoint/resume integration has passed; CI must still execute that integration test.
