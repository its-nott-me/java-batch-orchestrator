package dev.vivek.orchestrator.persistence;

import dev.vivek.orchestrator.domain.CreateJobCommand;
import dev.vivek.orchestrator.domain.JobRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

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

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "orchestrator.dispatch-initial-delay-ms=3600000",
        "orchestrator.heartbeat-initial-delay-ms=3600000",
        "orchestrator.recovery-initial-delay-ms=3600000"
})
class JobRepositoryPostgresTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("orchestrator_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JobRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper jsonMapper;

    @BeforeEach
    void clearJobs() {
        jdbc.update("TRUNCATE TABLE jobs");
    }

    @Test
    void idempotencyKeyReturnsTheOriginalJob() {
        CreateJobCommand command = command("same-request-key");
        var first = repository.create(command);
        var replay = repository.create(command);

        assertEquals(first.job().id(), replay.job().id());
        assertEquals(true, first.created());
        assertEquals(false, replay.created());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM jobs", Integer.class));
    }

    @Test
    void concurrentWorkersNeverClaimTheSameJob() throws Exception {
        int jobsToCreate = 40;
        for (int i = 0; i < jobsToCreate; i++) {
            repository.create(new CreateJobCommand("ECHO", jsonMapper.createObjectNode(), 3, 0, null, null, "unit-test-fingerprint"));
        }

        int workerCount = 8;
        CyclicBarrier barrier = new CyclicBarrier(workerCount);
        ExecutorService pool = Executors.newFixedThreadPool(workerCount);
        try {
            List<Future<List<UUID>>> futures = new ArrayList<>();
            for (int i = 0; i < workerCount; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await();
                    List<UUID> claimed = new ArrayList<>();
                    UUID workerId = UUID.randomUUID();
                    while (true) {
                        var next = repository.claimNext(workerId, 60);
                        if (next.isEmpty()) break;
                        claimed.add(next.get().id());
                    }
                    return claimed;
                }));
            }

            List<UUID> allClaims = new ArrayList<>();
            for (Future<List<UUID>> future : futures) allClaims.addAll(future.get());
            Set<UUID> uniqueClaims = new HashSet<>(allClaims);
            assertEquals(jobsToCreate, allClaims.size());
            assertEquals(jobsToCreate, uniqueClaims.size());
            assertEquals(jobsToCreate, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM jobs WHERE status = 'RUNNING'", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    private CreateJobCommand command(String key) {
        return new CreateJobCommand("ECHO", jsonMapper.createObjectNode(), 3, 0, null, key, "unit-test-fingerprint");
    }
}
