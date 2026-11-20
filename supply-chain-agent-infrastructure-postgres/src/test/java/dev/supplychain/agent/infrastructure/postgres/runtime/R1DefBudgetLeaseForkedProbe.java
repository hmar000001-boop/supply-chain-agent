package dev.supplychain.agent.infrastructure.postgres.runtime;

import dev.supplychain.agent.infrastructure.postgres.config.PostgresDataSourceFactory;
import dev.supplychain.agent.infrastructure.postgres.config.PostgresDatabaseConfig;
import dev.supplychain.agent.mechanisms.b6.ResourceUsage;
import dev.supplychain.agent.mechanisms.b6.ResourceUnit;

import java.time.Instant;
import java.util.Map;

/** Child JVM used by R1-DEF to simulate abrupt process death with System.halt (no shutdown hooks). */
public final class R1DefBudgetLeaseForkedProbe {
    private R1DefBudgetLeaseForkedProbe() { }

    public static void main(String[] args) {
        if (args.length != 7) throw new IllegalArgumentException("expected mode,scope,execution,reservation,owner,baseTime,leaseUntil");
        String mode = args[0];
        String scope = args[1];
        String execution = args[2];
        String reservation = args[3];
        String owner = args[4];
        Instant baseTime = Instant.parse(args[5]);
        Instant leaseUntil = Instant.parse(args[6]);
        var dataSource = PostgresDataSourceFactory.create(PostgresDatabaseConfig.fromEnvironment(System.getenv()));
        var ledger = new PostgresResourceBudgetLedger(dataSource, scope);
        var lease = ledger.acquireLease(execution, reservation, owner,
                new ResourceUsage(Map.of(ResourceUnit.MODEL_CALLS, 2L,
                        ResourceUnit.REASONING_EVALUATIONS, 1L)), leaseUntil, baseTime);
        if ("started".equals(mode)) {
            ledger.markOperationStarted(execution, reservation, owner, lease.fencingToken(), baseTime);
        } else if (!"reserved".equals(mode)) {
            throw new IllegalArgumentException("unknown probe mode: " + mode);
        }
        System.out.println("R1DEF_LEASE_HELD mode=" + mode + " token=" + lease.fencingToken());
        System.out.flush();
        Runtime.getRuntime().halt(0);
    }
}
