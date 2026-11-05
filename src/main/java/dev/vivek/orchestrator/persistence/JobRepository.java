package dev.vivek.orchestrator.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import dev.vivek.orchestrator.domain.CreateJobCommand;
import dev.vivek.orchestrator.domain.JobRecord;
import dev.vivek.orchestrator.domain.JobStatus;
import dev.vivek.orchestrator.domain.JobSubmission;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class JobRepository {
    private static final String SELECT_COLUMNS = """
            id, job_type, payload, result, status, priority, attempt_count, max_attempts,
            run_at, created_at, updated_at, started_at, finished_at, last_error,
            idempotency_key, request_hash, worker_id, locked_until
            """;

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;
    private final RowMapper<JobRecord> rowMapper = this::mapRow;

    public JobRepository(JdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    public JobSubmission create(CreateJobCommand command) {
        String sql = """
                INSERT INTO jobs (
                    id, job_type, payload, status, priority, max_attempts, run_at, idempotency_key, request_hash
                ) VALUES (?, ?, CAST(? AS JSONB), 'QUEUED', ?, ?, COALESCE(?::timestamptz, CURRENT_TIMESTAMP), ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                RETURNING %s
                """.formatted(SELECT_COLUMNS);

        String payloadJson = jsonMapper.writeValueAsString(command.payload());
        Timestamp runAt = command.runAt() == null ? null : Timestamp.from(command.runAt().toInstant());
        List<JobRecord> inserted = jdbc.query(sql, rowMapper,
                UUID.randomUUID(), command.type(), payloadJson, command.priority(),
                command.maxAttempts(), runAt, command.idempotencyKey(), command.requestHash());

        if (!inserted.isEmpty()) {
            return new JobSubmission(inserted.getFirst(), true);
        }

        // The only expected conflict is the unique idempotency key.
        if (command.idempotencyKey() != null) {
            return findByIdempotencyKey(command.idempotencyKey())
                    .map(existing -> new JobSubmission(existing, false))
                    .orElseThrow(() -> new IllegalStateException("Insert conflicted but the existing job was not found"));
        }
        throw new IllegalStateException("Job insert did not return a row");
    }

    public Optional<JobRecord> findById(UUID id) {
        String sql = "SELECT %s FROM jobs WHERE id = ?".formatted(SELECT_COLUMNS);
        return jdbc.query(sql, rowMapper, id).stream().findFirst();
    }

    public Optional<JobRecord> findByIdempotencyKey(String key) {
        String sql = "SELECT %s FROM jobs WHERE idempotency_key = ?".formatted(SELECT_COLUMNS);
        return jdbc.query(sql, rowMapper, key).stream().findFirst();
    }


    public long countPending() {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM jobs
                WHERE status IN ('QUEUED', 'RETRY_WAIT')
                """, Long.class);
    }

    public long countReady() {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM jobs
                WHERE status IN ('QUEUED', 'RETRY_WAIT')
                AND run_at <= CURRENT_TIMESTAMP
                """, Long.class);
    }

    public List<JobRecord> findRecent(JobStatus status, int limit) {
        if (status == null) {
            String sql = "SELECT %s FROM jobs ORDER BY created_at DESC LIMIT ?".formatted(SELECT_COLUMNS);
            return jdbc.query(sql, rowMapper, limit);
        }
        String sql = "SELECT %s FROM jobs WHERE status = ? ORDER BY created_at DESC LIMIT ?".formatted(SELECT_COLUMNS);
        return jdbc.query(sql, rowMapper, status.name(), limit);
    }

    public Optional<JobRecord> claimNext(UUID workerId, int leaseDurationSeconds) {
        String sql = """
                WITH candidate AS (
                    SELECT id
                    FROM jobs
                    WHERE status IN ('QUEUED', 'RETRY_WAIT')
                      AND run_at <= CURRENT_TIMESTAMP
                    ORDER BY priority DESC, run_at ASC, created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE jobs AS job
                SET status = 'RUNNING',
                    attempt_count = job.attempt_count + 1,
                    started_at = COALESCE(job.started_at, CURRENT_TIMESTAMP),
                    worker_id = ?,
                    locked_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                    updated_at = CURRENT_TIMESTAMP,
                    last_error = NULL,
                    version = job.version + 1
                FROM candidate
                WHERE job.id = candidate.id
                RETURNING job.*
                """;
        return jdbc.query(sql, rowMapper, workerId, leaseDurationSeconds).stream().findFirst();
    }

    public boolean renewLease(UUID jobId, UUID workerId, int leaseDurationSeconds) {
        int changed = jdbc.update("""
                UPDATE jobs
                SET locked_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'RUNNING' AND worker_id = ?
                """, leaseDurationSeconds, jobId, workerId);
        return changed == 1;
    }

    public boolean markSucceeded(UUID jobId, UUID workerId, JsonNode result) {
        String resultJson = result == null ? "null" : jsonMapper.writeValueAsString(result);
        int changed = jdbc.update("""
                UPDATE jobs
                SET status = 'SUCCEEDED',
                    result = CAST(? AS JSONB),
                    finished_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP,
                    worker_id = NULL,
                    locked_until = NULL,
                    version = version + 1
                WHERE id = ? AND status = 'RUNNING' AND worker_id = ?
                """, resultJson, jobId, workerId);
        return changed == 1;
    }

    public boolean markFailedOrRetry(UUID jobId, UUID workerId, String error,
                                     boolean retry, int retryDelaySeconds) {
        String nextStatus = retry ? JobStatus.RETRY_WAIT.name() : JobStatus.FAILED.name();
        String safeError = error == null ? "Unknown job execution error" : error.substring(0, Math.min(error.length(), 2000));
        int changed = jdbc.update("""
                UPDATE jobs
                SET status = ?,
                    run_at = CASE WHEN ? = 'RETRY_WAIT'
                                  THEN CURRENT_TIMESTAMP + (? * INTERVAL '1 second')
                                  ELSE run_at END,
                    last_error = ?,
                    finished_at = CASE WHEN ? = 'FAILED' THEN CURRENT_TIMESTAMP ELSE NULL END,
                    updated_at = CURRENT_TIMESTAMP,
                    worker_id = NULL,
                    locked_until = NULL,
                    version = version + 1
                WHERE id = ? AND status = 'RUNNING' AND worker_id = ?
                """, nextStatus, nextStatus, retryDelaySeconds, safeError, nextStatus, jobId, workerId);
        return changed == 1;
    }

    public boolean releaseClaim(UUID jobId, UUID workerId) {
        int changed = jdbc.update("""
                UPDATE jobs
                SET status = 'QUEUED',
                    attempt_count = GREATEST(attempt_count - 1, 0),
                    worker_id = NULL,
                    locked_until = NULL,
                    updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE id = ? AND status = 'RUNNING' AND worker_id = ?
                """, jobId, workerId);
        return changed == 1;
    }

    public boolean cancelPending(UUID jobId) {
        int changed = jdbc.update("""
                UPDATE jobs
                SET status = 'CANCELLED', finished_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE id = ? AND status IN ('QUEUED', 'RETRY_WAIT')
                """, jobId);
        return changed == 1;
    }

    public int recoverExpiredLeases() {
        return jdbc.update("""
                UPDATE jobs
                SET status = CASE WHEN attempt_count < max_attempts THEN 'RETRY_WAIT' ELSE 'FAILED' END,
                    run_at = CASE WHEN attempt_count < max_attempts
                                  THEN CURRENT_TIMESTAMP + INTERVAL '2 seconds'
                                  ELSE run_at END,
                    last_error = 'Worker lease expired; execution outcome is unknown.',
                    finished_at = CASE WHEN attempt_count < max_attempts THEN NULL ELSE CURRENT_TIMESTAMP END,
                    worker_id = NULL,
                    locked_until = NULL,
                    updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE status = 'RUNNING' AND locked_until < CURRENT_TIMESTAMP
                """);
    }

    private JobRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        try {
            JsonNode payload = jsonMapper.readTree(rs.getString("payload"));
            String resultJson = rs.getString("result");
            JsonNode result = resultJson == null ? null : jsonMapper.readTree(resultJson);
            Object workerValue = rs.getObject("worker_id");
            UUID workerId = workerValue == null ? null : (UUID) workerValue;
            return new JobRecord(
                    rs.getObject("id", UUID.class),
                    rs.getString("job_type"),
                    payload,
                    result,
                    JobStatus.valueOf(rs.getString("status")),
                    rs.getInt("priority"),
                    rs.getInt("attempt_count"),
                    rs.getInt("max_attempts"),
                    toOffsetDateTime(rs, "run_at"),
                    toOffsetDateTime(rs, "created_at"),
                    toOffsetDateTime(rs, "updated_at"),
                    toOffsetDateTime(rs, "started_at"),
                    toOffsetDateTime(rs, "finished_at"),
                    rs.getString("last_error"),
                    rs.getString("idempotency_key"),
                    rs.getString("request_hash"),
                    workerId,
                    toOffsetDateTime(rs, "locked_until")
            );
        } catch (JacksonException ex) {
            throw new SQLException("Could not deserialize JSONB stored for job " + rs.getString("id"), ex);
        }
    }

    private OffsetDateTime toOffsetDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant().atOffset(ZoneOffset.UTC);
    }
}
