package com.momna.platform.jobs;

public interface JobHandler {
    JobType type();
    JobResult handle(JobRecord job);

    record JobResult(String resultRef) {}
}
