package dev.vivek.orchestrator.application;

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.vivek.orchestrator.config.OrchestratorProperties;
import dev.vivek.orchestrator.domain.ExponentialBackoff;
import dev.vivek.orchestrator.domain.JobRecord;
import dev.vivek.orchestrator.domain.PermanentJobException;
import dev.vivek.orchestrator.handler.JobExecutionContext;
import dev.vivek.orchestrator.handler.JobHandler;
import dev.vivek.orchestrator.handler.JobHandlerRegistry;
import dev.vivek.orchestrator.observability.OrchestratorMetrics;
import dev.vivek.orchestrator.persistence.JobRepository;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class JobExecutionService {
    private static final Logger log = LoggerFactory.getLogger(JobExecutionService.class);

    private final JobRepository repository;
    private final JobHandlerRegistry handlerRegistry;
    private final JsonMapper jsonMapper;
    private final OrchestratorProperties properties;
    private final ConcurrentMap<UUID, UUID> activeJobs;
    private final Semaphore workerPermits;
    private final MeterRegistry meterRegistry;
    private final OrchestratorMetrics metrics;

    public JobExecutionService(JobRepository repository,
                               JobHandlerRegistry handlerRegistry,
                               JsonMapper jsonMapper,
                               OrchestratorProperties properties,
                               ConcurrentMap<UUID, UUID> activeJobs,
                               Semaphore workerPermits,
                               MeterRegistry meterRegistry,
                               OrchestratorMetrics metrics) {
        this.repository = repository;
        this.handlerRegistry = handlerRegistry;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
        this.activeJobs = activeJobs;
        this.workerPermits = workerPermits;
        this.meterRegistry = meterRegistry;
        this.metrics = metrics;
    }

    public void execute(JobRecord job) {
        UUID workerId = job.workerId();
        activeJobs.put(job.id(), workerId);
        try {
            JobHandler handler = handlerRegistry.require(job.type());
            JsonNode result;

            long handlerStarted = System.nanoTime();

            try {
                result = handler.execute(
                        job.payload(),
                        new JobExecutionContext(
                                job.id(),
                                job.attemptCount(),
                                job.maxAttempts()
                        )
                );
            } finally {
                metrics.recordHandlerDuration(
                        job.type(),
                        System.nanoTime() - handlerStarted
                );
            }
            if (repository.markSucceeded(job.id(), workerId, result)) {
                meterRegistry.counter("orchestrator.jobs.succeeded", "job_type", job.type()).increment();
                log.info("job.succeeded jobId={} type={} attempt={}", job.id(), job.type(), job.attemptCount());
            } else {
                log.warn("job.result_not_persisted jobId={} workerId={}; ownership may have expired", job.id(), workerId);
            }
        } catch (PermanentJobException ex) {
            persistFailure(job, workerId, ex, false);
        } catch (InterruptedException ex) {
            persistFailure(job, workerId, ex, job.attemptCount() < job.maxAttempts());
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            persistFailure(job, workerId, ex, job.attemptCount() < job.maxAttempts());
        } finally {
            activeJobs.remove(job.id(), workerId);
            workerPermits.release();
        }
    }

    private void persistFailure(JobRecord job, UUID workerId, Exception exception, boolean retry) {
        String error = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
        int delay = ExponentialBackoff.delaySeconds(
                job.attemptCount(),
                properties.getBaseRetryDelaySeconds(),
                properties.getMaxRetryDelaySeconds()
        );
        try {
            boolean updated = repository.markFailedOrRetry(job.id(), workerId, error, retry, delay);
            if (!updated) {
                log.warn("job.failure_not_persisted jobId={} workerId={}; ownership may have expired", job.id(), workerId);
                return;
            }
            if (retry) {
                meterRegistry.counter("orchestrator.jobs.retried", "job_type", job.type()).increment();
                log.warn("job.retry_scheduled jobId={} type={} attempt={} delaySeconds={} error={}",
                        job.id(), job.type(), job.attemptCount(), delay, error);
            } else {
                meterRegistry.counter("orchestrator.jobs.failed", "job_type", job.type()).increment();
                log.error("job.failed jobId={} type={} attempt={} error={}",
                        job.id(), job.type(), job.attemptCount(), error);
            }
        } catch (Exception persistenceFailure) {
            log.error("job.failure_persistence_error jobId={}; lease recovery will reconcile it", job.id(), persistenceFailure);
        }
    }
}
