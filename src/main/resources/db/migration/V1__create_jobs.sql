CREATE TABLE jobs (
    id              UUID PRIMARY KEY,
    job_type        VARCHAR(50) NOT NULL,
    payload         JSONB NOT NULL,
    result          JSONB,
    status          VARCHAR(20) NOT NULL,
    priority        INTEGER NOT NULL DEFAULT 0,
    attempt_count   INTEGER NOT NULL DEFAULT 0,
    max_attempts    INTEGER NOT NULL DEFAULT 3,
    run_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    last_error      VARCHAR(2000),
    idempotency_key VARCHAR(120),
    request_hash    VARCHAR(64) NOT NULL,
    worker_id       UUID,
    locked_until    TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT jobs_status_check CHECK (
        status IN ('QUEUED', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT jobs_attempt_count_check CHECK (attempt_count >= 0),
    CONSTRAINT jobs_max_attempts_check CHECK (max_attempts BETWEEN 1 AND 10),
    CONSTRAINT jobs_priority_check CHECK (priority BETWEEN -10 AND 10)
);

-- PostgreSQL permits multiple NULL values in a normal unique index.
CREATE UNIQUE INDEX jobs_idempotency_key_uq
    ON jobs (idempotency_key);

CREATE INDEX jobs_dispatch_idx
    ON jobs (priority DESC, run_at ASC, created_at ASC)
    WHERE status IN ('QUEUED', 'RETRY_WAIT');

CREATE INDEX jobs_expired_lease_idx
    ON jobs (locked_until)
    WHERE status = 'RUNNING';

CREATE INDEX jobs_status_created_idx
    ON jobs (status, created_at DESC);
