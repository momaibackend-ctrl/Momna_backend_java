package com.momna.platform.ai;

import com.momna.platform.ai.AiContracts.Cost;
import com.momna.platform.ai.AiContracts.TokenUsage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The AI gateway's cost ledger in PostgreSQL ({@code momna.ai_cost_ledger}). A policy window key
 * {@code utc-day} is one UTC calendar day; any other key is used as it is. The subject is stored as a
 * SHA-256 of the user id.
 */
public class JdbcAiCostLedger implements AiPorts.CostLedger {
    public static final String UTC_DAY = "utc-day";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcAiCostLedger(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public long spentMicros(String subjectUserId, String purpose, String windowKey) {
        Long spent = jdbc.queryForObject("""
            SELECT COALESCE(SUM(cost_micros), 0) FROM momna.ai_cost_ledger
            WHERE subject_hash = ? AND purpose = ? AND window_key = ?
            """, Long.class, sha(subjectUserId), purpose, window(windowKey));
        return spent == null ? 0 : spent;
    }

    @Override
    public void record(String subjectUserId, String purpose, String requestId, String candidateRef,
                       TokenUsage usage, Cost cost, String windowKey) {
        jdbc.update("""
            INSERT INTO momna.ai_cost_ledger(subject_hash, purpose, request_id, candidate_ref,
                input_tokens, output_tokens, cost_micros, window_key, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, sha(subjectUserId), purpose, requestId, candidateRef, usage.inputTokens(), usage.outputTokens(),
            cost.amountMicros(), window(windowKey), Timestamp.from(clock.instant()));
    }

    String window(String windowKey) {
        return UTC_DAY.equals(windowKey) ? UTC_DAY + ":" + LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC) : windowKey;
    }

    static String sha(String value) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            var out = new StringBuilder(64);
            for (byte b : bytes) out.append("%02x".formatted(b & 0xff));
            return out.toString();
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }
}
