package dev.vivek.orchestrator.handler;

import java.util.UUID;

public record JobExecutionContext(UUID jobId, int attemptNumber, int maxAttempts) { }
