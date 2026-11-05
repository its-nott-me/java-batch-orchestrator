package dev.vivek.orchestrator.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum JobStatus {
    QUEUED,
    RUNNING,
    RETRY_WAIT,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    private static final Map<JobStatus, Set<JobStatus>> TRANSITIONS = Map.of(
            QUEUED, EnumSet.of(RUNNING, CANCELLED),
            RUNNING, EnumSet.of(SUCCEEDED, RETRY_WAIT, FAILED),
            RETRY_WAIT, EnumSet.of(RUNNING, CANCELLED),
            SUCCEEDED, EnumSet.noneOf(JobStatus.class),
            FAILED, EnumSet.noneOf(JobStatus.class),
            CANCELLED, EnumSet.noneOf(JobStatus.class)
    );

    public boolean canTransitionTo(JobStatus next) {
        return TRANSITIONS.get(this).contains(next);
    }
}
