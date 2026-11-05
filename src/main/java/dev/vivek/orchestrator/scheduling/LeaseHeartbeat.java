package dev.vivek.orchestrator.scheduling;

import dev.vivek.orchestrator.config.OrchestratorProperties;
import dev.vivek.orchestrator.persistence.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class LeaseHeartbeat {
    private static final Logger log = LoggerFactory.getLogger(LeaseHeartbeat.class);

    private final ActiveJobRegistry activeJobRegistry;
    private final JobRepository repository;
    private final OrchestratorProperties properties;

    public LeaseHeartbeat(ActiveJobRegistry activeJobRegistry,
                          JobRepository repository,
                          OrchestratorProperties properties) {
        this.activeJobRegistry = activeJobRegistry;
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${orchestrator.heartbeat-interval-ms:10000}",
            initialDelayString = "${orchestrator.heartbeat-initial-delay-ms:10000}"
    )
    public void renewActiveLeases() {
        for (Map.Entry<UUID, UUID> entry : activeJobRegistry.jobs().entrySet()) {
            try {
                boolean renewed = repository.renewLease(
                        entry.getKey(), entry.getValue(), properties.getLeaseDurationSeconds()
                );
                if (!renewed) {
                    log.warn("job.lease_renewal_lost jobId={} workerId={}", entry.getKey(), entry.getValue());
                }
            } catch (Exception ex) {
                log.error("job.lease_renewal_error jobId={}", entry.getKey(), ex);
            }
        }
    }
}
