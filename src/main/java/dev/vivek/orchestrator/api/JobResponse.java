package dev.vivek.orchestrator.api;

import dev.vivek.orchestrator.domain.JobRecord;
import dev.vivek.orchestrator.domain.JobStatus;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobResponse(
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
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String lastError
) {
    public static JobResponse from(JobRecord job) {
        return new JobResponse(
                job.id(), job.type(), job.payload(), job.result(), job.status(), job.priority(),
                job.attemptCount(), job.maxAttempts(), job.runAt(), job.createdAt(),
                job.startedAt(), job.finishedAt(), job.lastError()
        );
    }
}
