package dev.vivek.orchestrator.persistence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import dev.vivek.orchestrator.domain.CreateJobCommand;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfSystemProperty(
        named = "nativePostgres",
        matches = "true"
)
@ActiveProfiles("native-test")
@SpringBootTest(properties = {
        "orchestrator.dispatch-initial-delay-ms=3600000",
        "orchestrator.heartbeat-initial-delay-ms=3600000",
        "orchestrator.recovery-initial-delay-ms=3600000"
})
class JobRepositoryNativePostgresTest {

    @Autowired
    JobRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JsonMapper jsonMapper;

    @BeforeEach
    void clearJobs() {
        jdbc.update("TRUNCATE TABLE jobs");
    }

    @Test
    void idempotencyKeyReturnsOriginalJob() {
        var command = new CreateJobCommand(
                "ECHO",
                jsonMapper.createObjectNode(),
                3, 0, null,
                "native-test-key",
                "unit-test-fingerprint"
        );

        var first = repository.create(command);
        var replay = repository.create(command);

        assertEquals(first.job().id(), replay.job().id());
        assertTrue(first.created());
        assertFalse(replay.created());

        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM jobs",
                        Integer.class
                )
        );
    }

    @Test
    void concurrentWorkersNeverClaimSameJob() throws Exception {
        int jobCount = 40;
        int workerCount = 8;

        for (int i = 0; i < jobCount; i++) {
            repository.create(new CreateJobCommand(
                    "ECHO",
                    jsonMapper.createObjectNode(),
                    3, 0, null, null,
                    "test-fingerprint"
            ));
        }

        CyclicBarrier barrier = new CyclicBarrier(workerCount);
        ExecutorService pool =
                Executors.newFixedThreadPool(workerCount);

        try {
            List<Future<List<UUID>>> futures = new ArrayList<>();

            for (int i = 0; i < workerCount; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await();

                    List<UUID> claimed = new ArrayList<>();
                    UUID workerId = UUID.randomUUID();

                    while (true) {
                        var job = repository.claimNext(workerId, 60);

                        if (job.isEmpty()) break;

                        claimed.add(job.get().id());
                    }

                    return claimed;
                }));
            }

            List<UUID> allClaims = new ArrayList<>();

            for (var future : futures) {
                allClaims.addAll(future.get());
            }

            Set<UUID> uniqueClaims = new HashSet<>(allClaims);

            assertEquals(jobCount, allClaims.size());
            assertEquals(jobCount, uniqueClaims.size());

            assertEquals(
                    jobCount,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM jobs WHERE status = 'RUNNING'",
                            Integer.class
                    )
            );

        } finally {
            pool.shutdownNow();
        }
    }
}
