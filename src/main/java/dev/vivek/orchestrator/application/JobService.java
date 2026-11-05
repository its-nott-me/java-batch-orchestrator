package dev.vivek.orchestrator.application;

import dev.vivek.orchestrator.domain.CreateJobCommand;
import dev.vivek.orchestrator.domain.JobConflictException;
import dev.vivek.orchestrator.domain.JobNotFoundException;
import dev.vivek.orchestrator.domain.JobRecord;
import dev.vivek.orchestrator.domain.JobStatus;
import dev.vivek.orchestrator.domain.JobSubmission;
import dev.vivek.orchestrator.persistence.JobRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class JobService {
    private final JobRepository repository;
    private final MeterRegistry meterRegistry;

    public JobService(JobRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    public JobSubmission submit(String type, JsonNode payload, Integer maxAttempts,
                                Integer priority, OffsetDateTime runAt, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        int effectiveMaxAttempts = maxAttempts == null ? 3 : maxAttempts;
        int effectivePriority = priority == null ? 0 : priority;
        String requestHash = RequestFingerprint.sha256(
                type, payload, effectiveMaxAttempts, effectivePriority, runAt
        );
        CreateJobCommand command = new CreateJobCommand(
                type, payload, effectiveMaxAttempts, effectivePriority, runAt, normalizedKey, requestHash
        );
        JobSubmission submission = repository.create(command);
        if (!submission.created() && !requestHash.equals(submission.job().requestHash())) {
            throw new JobConflictException("Idempotency-Key has already been used for a different request");
        }
        if (submission.created()) {
            meterRegistry.counter("orchestrator.jobs.submitted", "job_type", type).increment();
        } else {
            meterRegistry.counter("orchestrator.jobs.idempotent_replays", "job_type", type).increment();
        }
        return submission;
    }

    public JobRecord get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + id));
    }

    public List<JobRecord> list(JobStatus status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return repository.findRecent(status, safeLimit);
    }

    public JobRecord cancel(UUID id) {
        if (repository.cancelPending(id)) {
            meterRegistry.counter("orchestrator.jobs.cancelled").increment();
            return get(id);
        }

        JobRecord current = get(id);
        if (current.status() == JobStatus.CANCELLED) {
            return current;
        }
        if (current.status() == JobStatus.RUNNING) {
            throw new JobConflictException("A running job cannot be cancelled by this API. Make the handler cooperative before adding running cancellation.");
        }
        throw new JobConflictException("Job in state " + current.status() + " cannot be cancelled.");
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) return null;
        String normalized = key.trim();
        if (normalized.length() > 120) {
            throw new IllegalArgumentException("Idempotency-Key must be 120 characters or fewer");
        }
        return normalized;
    }
}
