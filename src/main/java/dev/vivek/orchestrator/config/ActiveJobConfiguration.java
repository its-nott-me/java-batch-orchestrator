package dev.vivek.orchestrator.config;

import dev.vivek.orchestrator.scheduling.ActiveJobRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

@Configuration
public class ActiveJobConfiguration {
    @Bean
    public ConcurrentMap<UUID, UUID> activeJobs(ActiveJobRegistry registry) {
        return registry.jobs();
    }
}
