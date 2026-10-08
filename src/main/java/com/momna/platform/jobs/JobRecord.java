package com.momna.platform.jobs;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record JobRecord(
    UUID id,
    JobType type,
    String payloadRef,
    String idempotencyKey,
    JobStatus status,
    int attempts,
    int maxAttempts,
    Duration retryBackoff,
    String traceId,
    Instant createdAt,
    Instant startedAt,
    Instant finishedAt,
    int payloadSchemaVersion,
    String resultRef,
    String failureCode,
    Boolean failureRetryable
) {}
