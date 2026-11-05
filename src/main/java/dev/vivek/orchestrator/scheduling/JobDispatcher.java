package dev.vivek.orchestrator.scheduling;

import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.vivek.orchestrator.application.JobExecutionService;
import dev.vivek.orchestrator.config.OrchestratorProperties;
import dev.vivek.orchestrator.config.WorkerIdentity;
import dev.vivek.orchestrator.domain.JobRecord;
import dev.vivek.orchestrator.observability.OrchestratorMetrics;
import dev.vivek.orchestrator.persistence.JobRepository;

@Component
public class JobDispatcher {
    private static final Logger log = LoggerFactory.getLogger(JobDispatcher.class);

    private final JobRepository repository;
    private final JobExecutionService executionService;
    private final OrchestratorProperties properties;
    private final WorkerIdentity workerIdentity;
    private final Semaphore workerPermits;
    private final ThreadPoolExecutor executor;
    private final OrchestratorMetrics metrics;

    public JobDispatcher(
            JobRepository repository,
            JobExecutionService executionService,
            OrchestratorProperties properties,
            WorkerIdentity workerIdentity,
            Semaphore workerPermits,
            ThreadPoolExecutor executor,
            OrchestratorMetrics metrics
    ) {
        this.repository = repository;
        this.executionService = executionService;
        this.properties = properties;
        this.workerIdentity = workerIdentity;
        this.workerPermits = workerPermits;
        this.executor = executor;
        this.metrics = metrics;
    }

    @Scheduled(
            fixedDelayString = "${orchestrator.dispatch-interval-ms:500}",
            initialDelayString = "${orchestrator.dispatch-initial-delay-ms:500}"
    )
    public void dispatch() {
        while (workerPermits.tryAcquire()) {
            JobRecord claimed = null;
            try {
                Optional<JobRecord> next = repository.claimNext(
                        workerIdentity.id(), properties.getLeaseDurationSeconds()
                );
                if (next.isEmpty()) {
                    workerPermits.release();
                    return;
                }
                claimed = next.get();
                JobRecord jobToRun = claimed;

                executor.execute(() -> executionService.execute(jobToRun));

                metrics.recordClaimed(claimed);
            } catch (RejectedExecutionException ex) {
                if (claimed != null) {
                    try {
                        repository.releaseClaim(claimed.id(), workerIdentity.id());
                    } catch (Exception releaseFailure) {
                        ex.addSuppressed(releaseFailure);
                        log.error("could_not_release_rejected_claim jobId={}", claimed.id(), releaseFailure);
                    }
                }
                workerPermits.release();
                log.warn("worker_pool_rejected jobId={}; claim release attempted", claimed == null ? "unknown" : claimed.id());
                return;
            } catch (Exception ex) {
                if (claimed != null) {
                    try {
                        repository.releaseClaim(claimed.id(), workerIdentity.id());
                    } catch (Exception releaseFailure) {
                        ex.addSuppressed(releaseFailure);
                    }
                }
                workerPermits.release();
                log.error("job_dispatch_error; dispatch loop stopped", ex);
                return;
            }
        }
    }
}
