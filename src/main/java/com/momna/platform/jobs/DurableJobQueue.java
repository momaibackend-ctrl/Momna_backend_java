package com.momna.platform.jobs;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DurableJobQueue {
    private final JdbcTemplate jdbc;

    public DurableJobQueue(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public EnqueueResult enqueue(
        JobType type,
        String payloadRef,
        String idempotencyKey,
        String traceId,
        int maxAttempts,
        Duration initialBackoff,
        int payloadSchemaVersion
    ) {
        requireText(payloadRef, "payloadRef");
        requireText(idempotencyKey, "idempotencyKey");
        requireText(traceId, "traceId");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        if (initialBackoff == null || initialBackoff.isZero() || initialBackoff.isNegative()) {
            throw new IllegalArgumentException("initialBackoff must be positive");
        }
        if (payloadSchemaVersion < 1) {
            throw new IllegalArgumentException("payloadSchemaVersion must be positive");
        }

        var id = UUID.randomUUID();
        var inserted = jdbc.update(
            """
            insert into momna.platform_jobs(
                id,job_type,payload_ref,idempotency_key,payload_schema_version,status,
                attempts,max_attempts,retry_backoff_ms,trace_id,created_at
            ) values (?,?,?,?,?,'QUEUED',0,?,?,?,now())
            on conflict(idempotency_key) do nothing
            """,
            id,
            type.name(),
            payloadRef,
            idempotencyKey,
            payloadSchemaVersion,
            maxAttempts,
            initialBackoff.toMillis(),
            traceId
        );

        var job = byIdempotencyKey(idempotencyKey);
        return new EnqueueResult(job, inserted == 0);
    }

    @Transactional
    public JobRecord claimNext(String workerId, Duration lease) {
        requireText(workerId, "workerId");
        if (lease == null || lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("lease must be positive");
        }

        var selected = jdbc.query(
            """
            select * from momna.platform_jobs
            where attempts < max_attempts
              and (
                (status in ('QUEUED','RETRY_WAIT') and (retry_at is null or retry_at<=now()))
                or (status='RUNNING' and lease_expires_at<=now())
              )
            order by created_at
            for update skip locked
            limit 1
            """,
            (rs, rowNum) -> map(rs)
        );
        if (selected.isEmpty()) return null;

        var current = selected.getFirst();
        jdbc.update(
            """
            update momna.platform_jobs
            set status='RUNNING',
                attempts=attempts+1,
                started_at=coalesce(started_at,now()),
                worker_id=?,
                lease_expires_at=now() + (? * interval '1 millisecond'),
                finished_at=null
            where id=?
            """,
            workerId,
            lease.toMillis(),
            current.id()
        );
        return find(current.id());
    }

    @Transactional
    public void complete(UUID jobId, String resultRef, Instant finishedAt) {
        jdbc.update(
            """
            update momna.platform_jobs
            set status='SUCCEEDED',
                result_ref=?,
                failure_code=null,
                failure_retryable=null,
                finished_at=?,
                retry_at=null,
                lease_expires_at=null
            where id=?
            """,
            resultRef,
            Timestamp.from(finishedAt),
            jobId
        );
    }

    @Transactional
    public void fail(UUID jobId, String failureCode, boolean retryable, Instant finishedAt) {
        var job = find(jobId);
        var dead = retryable && job.attempts() >= job.maxAttempts();
        var status = !retryable
            ? JobStatus.FAILED
            : dead ? JobStatus.DEAD_LETTER : JobStatus.RETRY_WAIT;

        Instant retryAt = null;
        if (status == JobStatus.RETRY_WAIT) {
            var exponent = Math.max(0, Math.min(job.attempts() - 1, 20));
            var multiplier = 1L << exponent;
            retryAt = finishedAt.plusMillis(job.retryBackoff().toMillis() * multiplier);
        }

        jdbc.update(
            """
            update momna.platform_jobs
            set status=?,
                retry_at=?,
                finished_at=?,
                failure_code=?,
                failure_retryable=?,
                lease_expires_at=null
            where id=?
            """,
            status.name(),
            retryAt == null ? null : Timestamp.from(retryAt),
            Timestamp.from(finishedAt),
            failureCode,
            retryable,
            jobId
        );
    }

    @Transactional
    public boolean cancel(UUID jobId) {
        return jdbc.update(
            """
            update momna.platform_jobs
            set status='CANCELLED', finished_at=now()
            where id=? and status in ('QUEUED','RETRY_WAIT')
            """,
            jobId
        ) == 1;
    }

    @Transactional(readOnly = true)
    public boolean health() {
        try {
            return Boolean.TRUE.equals(jdbc.queryForObject("select true", Boolean.class));
        } catch (RuntimeException failure) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public JobRecord find(UUID jobId) {
        var values = jdbc.query(
            "select * from momna.platform_jobs where id=?",
            (rs, rowNum) -> map(rs),
            jobId
        );
        if (values.isEmpty()) throw new IllegalArgumentException("Job not found");
        return values.getFirst();
    }

    private JobRecord byIdempotencyKey(String idempotencyKey) {
        var values = jdbc.query(
            "select * from momna.platform_jobs where idempotency_key=?",
            (rs, rowNum) -> map(rs),
            idempotencyKey
        );
        if (values.isEmpty()) throw new IllegalStateException("Idempotent job insert did not resolve");
        return values.getFirst();
    }

    private JobRecord map(ResultSet rs) throws SQLException {
        var started = rs.getTimestamp("started_at");
        var finished = rs.getTimestamp("finished_at");
        var failureRetryable = rs.getObject("failure_retryable");

        return new JobRecord(
            rs.getObject("id", UUID.class),
            JobType.valueOf(rs.getString("job_type")),
            rs.getString("payload_ref"),
            rs.getString("idempotency_key"),
            JobStatus.valueOf(rs.getString("status")),
            rs.getInt("attempts"),
            rs.getInt("max_attempts"),
            Duration.ofMillis(rs.getLong("retry_backoff_ms")),
            rs.getString("trace_id"),
            rs.getTimestamp("created_at").toInstant(),
            started == null ? null : started.toInstant(),
            finished == null ? null : finished.toInstant(),
            rs.getInt("payload_schema_version"),
            rs.getString("result_ref"),
            rs.getString("failure_code"),
            failureRetryable == null ? null : rs.getBoolean("failure_retryable")
        );
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    public record EnqueueResult(JobRecord job, boolean idempotentReplay) {}
}
