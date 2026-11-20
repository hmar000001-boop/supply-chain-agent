package dev.supplychain.agent.infrastructure.postgres.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves a real JVM process boundary: the first child exits after checkpointing an interrupted
 * workflow and the second child starts from scratch, reconnects to PostgreSQL, restores the
 * checkpoint/intention/approval/B6 reservation/M3 operation, and reconciles without redispatch.
 */
@EnabledIfSystemProperty(named = "phase0.postgres", matches = "true")
class M8B4ForkedJvmProcessRestartIntegrationTest {

    @Test
    void independentJvmRestartRestoresWorkflowAuthorityBudgetAndExternalOutcome() throws Exception {
        String scope = "m8b4d-forked-scope-" + UUID.randomUUID();
        String execution = "m8b4d-forked-execution-" + UUID.randomUUID();
        String threadId = dev.supplychain.agent.infrastructure.postgres.checkpoint.PostgresLangGraph4jCheckpointSaverFactory
                .threadIdForExecution(scope, execution);
        Path marker = Files.createTempFile("m8b4d-provider-effect-", ".marker");
        Files.deleteIfExists(marker);
        Instant baseTime = Instant.now().minusSeconds(5).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        try {
            runChild("write", scope, execution, threadId, marker, baseTime);
            assertTrue(Files.exists(marker), "the first JVM must leave the deterministic provider's committed effect behind");
            runChild("verify", scope, execution, threadId, marker, baseTime);
        } finally {
            Files.deleteIfExists(marker);
        }
    }

    private static void runChild(String mode, String scope, String execution, String threadId,
                                 Path marker, Instant baseTime) throws Exception {
        String classpath = System.getProperty("surefire.test.class.path");
        if (classpath == null || classpath.isBlank()) classpath = System.getProperty("java.class.path");
        assertNotNull(classpath);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path log = Files.createTempFile("m8b4d-" + mode + "-", ".log");
        Process process = new ProcessBuilder(java, "-cp", classpath,
                M8B4ForkedJvmProbe.class.getName(), mode, scope, execution, threadId,
                marker.toAbsolutePath().toString(), Long.toString(baseTime.toEpochMilli()))
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "forked JVM timed out in mode " + mode);
            String output = Files.readString(log, StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), "forked JVM " + mode + " failed:\n" + output);
            assertTrue(output.contains(mode.equals("write") ? "FIRST_PROCESS_CHECKPOINTED" : "REPLACEMENT_PROCESS_RECONCILED"),
                    "forked JVM did not report its completion marker:\n" + output);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(log);
        }
    }
}
