
package dev.vivek.orchestrator.observability;

import dev.vivek.orchestrator.config.WorkerIdentity;
import dev.vivek.orchestrator.domain.JobRecord;
import dev.vivek.orchestrator.persistence.JobRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class OrchestratorMetrics {

    private final MeterRegistry registry;

    public OrchestratorMetrics(
            MeterRegistry registry,
            JobRepository repository,
            ThreadPoolExecutor executor,
            Semaphore workerPermits
    ) {
        this.registry = registry;

        // Database queue metrics
        Gauge.builder(
                "orchestrator.jobs.pending",
                repository,
                repo -> repo.countPending()
        ).description("Jobs waiting for execution or retry")
         .register(registry);

        Gauge.builder(
                "orchestrator.jobs.ready",
                repository,
                repo -> repo.countReady()
        ).description("Jobs eligible for immediate execution")
         .register(registry);

        // Worker metrics
        Gauge.builder(
                "orchestrator.workers.active",
                executor,
                ThreadPoolExecutor::getActiveCount
        ).description("Currently executing worker threads")
         .register(registry);

        Gauge.builder(
                "orchestrator.workers.capacity",
                executor,
                ThreadPoolExecutor::getMaximumPoolSize
        ).description("Maximum worker thread capacity")
         .register(registry);

        Gauge.builder(
                "orchestrator.workers.utilization",
                executor,
                pool -> pool.getMaximumPoolSize() == 0
                        ? 0.0
                        : (double) pool.getActiveCount()
                          / pool.getMaximumPoolSize()
        ).description("Fraction of worker capacity currently occupied")
         .register(registry);

        Gauge.builder(
                "orchestrator.workers.available",
                workerPermits,
                Semaphore::availablePermits
        ).description("Available worker permits")
         .register(registry);
    }

    public void recordSubmitted(String jobType) {
        registry.counter(
                "orchestrator.jobs.submitted",
                "job_type", jobType
        ).increment();
    }

    public void recordClaimed(JobRecord job) {
        registry.counter(
                "orchestrator.jobs.claimed",
                "job_type", job.type()
        ).increment();

        if (job.runAt() != null) {
            long waitNanos = Duration.between(
                    job.runAt().toInstant(),
                    Instant.now()
            ).toNanos();

            Timer.builder("orchestrator.jobs.queue.wait")
                    .tag("job_type", job.type())
                    .description("Time between scheduled eligibility and job claim")
                    .publishPercentileHistogram()
                    .register(registry)
                    .record(Math.max(0, waitNanos), TimeUnit.NANOSECONDS);
        }
    }

    public void recordHandlerDuration(String jobType, long durationNanos) {
        Timer.builder("orchestrator.jobs.handler.duration")
                .tag("job_type", jobType)
                .description("Time spent executing job handlers")
                .publishPercentileHistogram()
                .register(registry)
                .record(Math.max(0, durationNanos), TimeUnit.NANOSECONDS);
    }
}
