package com.momna.modules.myday.application.generation;

import com.momna.modules.myday.application.template.MyDayTemplateKey;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The anonymous template cache ({@code momna.my_day_template_cache}, V40): the validated model answer for
 * one {@link MyDayTemplateKey} fingerprint and one model policy. Nothing personal is stored, so a row may
 * serve every user with the same key. TTL is at most 90 days.
 */
public class MyDayTemplateCache {
    public static final Duration MAX_TTL = Duration.ofDays(90);

    public record Entry(String cardsJson, String modelRef) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public MyDayTemplateCache(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Optional<Entry> find(MyDayTemplateKey key, String modelPolicy) {
        return jdbc.query("""
            SELECT cards::text AS cards, model_ref FROM momna.my_day_template_cache
            WHERE template_fingerprint = ? AND model_policy = ? AND expires_at > ?
            """, (rs, n) -> new Entry(rs.getString("cards"), rs.getString("model_ref")),
            key.fingerprint(), modelPolicy, Timestamp.from(clock.instant())).stream().findFirst();
    }

    /** Stores a validated answer; an unexpired row for the same key is kept, an expired one replaced. */
    public void put(MyDayTemplateKey key, String modelPolicy, String modelRef, String cardsJson, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("My Day template TTL must be within 90 days");
        }
        var now = clock.instant();
        jdbc.update("""
            INSERT INTO momna.my_day_template_cache(template_fingerprint, model_policy, key_version, life_mode,
                language, prompts_version, model_ref, cards, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
            ON CONFLICT (template_fingerprint, model_policy) DO UPDATE SET
                model_ref = EXCLUDED.model_ref, cards = EXCLUDED.cards,
                created_at = EXCLUDED.created_at, expires_at = EXCLUDED.expires_at
            WHERE momna.my_day_template_cache.expires_at <= EXCLUDED.created_at
            """, key.fingerprint(), modelPolicy, MyDayTemplateKey.KEY_VERSION, key.lifeMode(), key.userLanguage(),
            key.versions().prompts(), modelRef, cardsJson, Timestamp.from(now), Timestamp.from(now.plus(ttl)));
    }

    /** Removes a row whose content no longer passes validation. */
    public void evict(MyDayTemplateKey key, String modelPolicy) {
        jdbc.update("DELETE FROM momna.my_day_template_cache WHERE template_fingerprint = ? AND model_policy = ?",
            key.fingerprint(), modelPolicy);
    }
}
