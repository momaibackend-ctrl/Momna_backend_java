package com.momna.platform.jobs;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "momna.role", havingValue = "worker")
public class WorkerRuntime implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(WorkerRuntime.class);

    private final DurableJobQueue jobs;
    private final Map<JobType, JobHandler> handlers;
    private final String workerId;
    private final Duration pollInterval;
    private final Duration lease;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean active = new AtomicBoolean(false);
    private Thread thread;

    public WorkerRuntime(
        DurableJobQueue jobs,
        ObjectProvider<JobHandler> handlers,
        @Value("${momna.jobs.worker-id:${HOSTNAME:momna-worker}}") String workerId,
        @Value("${momna.jobs.poll-interval-ms:250}") long pollIntervalMs,
        @Value("${momna.jobs.lease-ms:120000}") long leaseMs
    ) {
        this.jobs = jobs;
        this.handlers = handlers.orderedStream().collect(
            java.util.stream.Collectors.toUnmodifiableMap(JobHandler::type, handler -> handler)
        );
        this.workerId = workerId;
        this.pollInterval = Duration.ofMillis(pollIntervalMs);
        this.lease = Duration.ofMillis(leaseMs);
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        thread = Thread.ofVirtual().name("momna-job-worker").start(this::runLoop);
    }

    private void runLoop() {
        while (running.get()) {
            try {
                if (handlers.isEmpty()) {
                    sleep();
                    continue;
                }

                var job = jobs.claimNext(workerId, lease);
                if (job == null) {
                    sleep();
                    continue;
                }

                active.set(true);
                try {
                    var handler = handlers.get(job.type());
                    if (handler == null) {
                        jobs.fail(job.id(), "handler_missing", false, Instant.now());
                        log.warn(
                            "job_failed job_id={} job_type={} attempt={} trace_id={} reason=handler_missing",
                            job.id(), job.type(), job.attempts(), job.traceId()
                        );
                        continue;
                    }

                    var result = handler.handle(job);
                    jobs.complete(
                        job.id(),
                        result == null ? null : result.resultRef(),
                        Instant.now()
                    );
                    log.info(
                        "job_completed job_id={} job_type={} attempt={} trace_id={}",
                        job.id(), job.type(), job.attempts(), job.traceId()
                    );
                } catch (Exception failure) {
                    jobs.fail(job.id(), "handler_failure", true, Instant.now());
                    log.warn(
                        "job_failed job_id={} job_type={} attempt={} trace_id={}",
                        job.id(), job.type(), job.attempts(), job.traceId()
                    );
                } finally {
                    active.set(false);
                }
            } catch (Exception failure) {
                log.warn("job_worker_poll_failed worker_id={}", workerId);
                sleep();
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(pollInterval);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }

    public boolean isLive() { return true; }
    public boolean isReady() { return running.get() && jobs.health(); }
    public boolean isIdle() { return !active.get(); }

    @Override
    public void stop() {
        running.set(false);
        if (thread != null) thread.interrupt();
    }

    @PreDestroy
    void shutdown() {
        stop();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return 0;
    }
}
