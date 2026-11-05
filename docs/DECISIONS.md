# Design decisions

## Why PostgreSQL is the queue

The project uses one durable store for both job state and queue coordination. PostgreSQL provides transactional row locks, `SKIP LOCKED`, indexes, JSONB payload storage, and unique constraints. This keeps the demonstration self-contained and makes the claim protocol inspectable.

## Why `SKIP LOCKED`

A worker selects the highest-priority due job while skipping rows currently locked by another claimant. The select and status update happen in one SQL statement. The database therefore acts as the concurrency coordinator across application instances.

## Why a lease

A worker can disappear after changing a job to `RUNNING`. `locked_until` makes abandoned claims detectable. Heartbeats extend the lease while the worker is alive; the recovery task retries or fails jobs after the lease expires.

## Why at-least-once

A worker can complete an external side effect and crash before persisting the result. No local transaction can atomically commit an arbitrary remote side effect and a PostgreSQL status update. Retrying is therefore safer than silently losing work, but handlers must be idempotent.

## Why a bounded executor + semaphore

The fixed worker pool caps concurrent execution. A semaphore reserves one permit before a database claim, preventing the dispatcher from claiming more jobs than the pool can immediately accept. A `SynchronousQueue` avoids an unbounded in-memory backlog; PostgreSQL remains the durable queue.

## Why JDBC instead of an ORM

The queue's critical operation is PostgreSQL-specific SQL (`FOR UPDATE SKIP LOCKED`, CTE + `UPDATE ... RETURNING`). Using JDBC keeps this path explicit and easy to inspect during an interview.
