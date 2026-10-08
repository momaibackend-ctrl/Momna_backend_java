package com.momna.platform.jobs;

public enum JobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    RETRY_WAIT,
    FAILED,
    DEAD_LETTER,
    CANCELLED
}
