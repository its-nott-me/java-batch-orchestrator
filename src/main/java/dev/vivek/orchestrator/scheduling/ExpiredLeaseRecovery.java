package dev.vivek.orchestrator.scheduling;

import dev.vivek.orchestrator.persistence.JobRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiredLeaseRecovery {
    private static final Logger log = LoggerFactory.getLogger(ExpiredLeaseRecovery.class);
    private final JobRepository repository;
    private final MeterRegistry meterRegistry;

    public ExpiredLeaseRecovery(JobRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(
            fixedDelayString = "${orchestrator.recovery-interval-ms:5000}",
            initialDelayString = "${orchestrator.recovery-initial-delay-ms:5000}"
    )
    public void recoverExpiredJobs() {
        try {
            int recovered = repository.recoverExpiredLeases();
            if (recovered > 0) {
                meterRegistry.counter("orchestrator.jobs.lease_recovered").increment(recovered);
                log.warn("job.expired_leases_recovered count={}", recovered);
            }
        } catch (Exception ex) {
            log.error("job.lease_recovery_error", ex);
        }
    }
}
