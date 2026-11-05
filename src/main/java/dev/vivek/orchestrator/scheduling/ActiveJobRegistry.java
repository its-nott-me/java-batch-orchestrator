package dev.vivek.orchestrator.scheduling;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ActiveJobRegistry {
    private final ConcurrentMap<UUID, UUID> activeJobs = new ConcurrentHashMap<>();

    public ConcurrentMap<UUID, UUID> jobs() {
        return activeJobs;
    }
}
