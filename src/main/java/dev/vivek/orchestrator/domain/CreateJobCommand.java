package dev.vivek.orchestrator.domain;

import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record CreateJobCommand(
        String type,
        JsonNode payload,
        int maxAttempts,
        int priority,
        OffsetDateTime runAt,
        String idempotencyKey,
        String requestHash
) { }
