package dev.vivek.orchestrator;

import dev.vivek.orchestrator.config.OrchestratorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(OrchestratorProperties.class)
public class BatchJobOrchestratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(BatchJobOrchestratorApplication.class, args);
    }
}
