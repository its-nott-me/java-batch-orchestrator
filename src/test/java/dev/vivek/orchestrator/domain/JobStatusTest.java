package dev.vivek.orchestrator.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobStatusTest {
    @Test
    void queuedAndRetryWaitJobsCanStartOrBeCancelled() {
        assertTrue(JobStatus.QUEUED.canTransitionTo(JobStatus.RUNNING));
        assertTrue(JobStatus.QUEUED.canTransitionTo(JobStatus.CANCELLED));
        assertTrue(JobStatus.RETRY_WAIT.canTransitionTo(JobStatus.RUNNING));
        assertTrue(JobStatus.RETRY_WAIT.canTransitionTo(JobStatus.CANCELLED));
    }

    @Test
    void runningJobsCanOnlyFinishOrWaitForRetry() {
        assertTrue(JobStatus.RUNNING.canTransitionTo(JobStatus.SUCCEEDED));
        assertTrue(JobStatus.RUNNING.canTransitionTo(JobStatus.RETRY_WAIT));
        assertTrue(JobStatus.RUNNING.canTransitionTo(JobStatus.FAILED));
        assertFalse(JobStatus.RUNNING.canTransitionTo(JobStatus.CANCELLED));
    }

    @Test
    void terminalStatesCannotTransition() {
        for (JobStatus terminal : new JobStatus[]{JobStatus.SUCCEEDED, JobStatus.FAILED, JobStatus.CANCELLED}) {
            for (JobStatus next : JobStatus.values()) {
                assertFalse(terminal.canTransitionTo(next));
            }
        }
    }
}
