package dev.vivek.orchestrator.domain;

import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobRecord(
        UUID id,
        String type,
        JsonNode payload,
        JsonNode result,
        JobStatus status,
        int priority,
        int attemptCount,
        int maxAttempts,
        OffsetDateTime runAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String lastError,
        String idempotencyKey,
        String requestHash,
        UUID workerId,
        OffsetDateTime lockedUntil
) { }
