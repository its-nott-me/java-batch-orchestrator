package dev.vivek.orchestrator.config;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WorkerIdentity {
    private final UUID id = UUID.randomUUID();

    public UUID id() {
        return id;
    }
}
