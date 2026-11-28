package com.momna.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.auth.application.EmailChallengeDelivery;
import com.momna.modules.auth.infrastructure.AuthSessionRepository;
import com.momna.modules.lifecycle.domain.LifecyclePeriod;
import com.momna.modules.lifecycle.infrastructure.*;
import com.momna.modules.myday.application.MyDayCardSource;
import com.momna.modules.myday.application.MyDayCards;
import com.momna.modules.myday.application.MyDayException;
import com.momna.modules.profile.infrastructure.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * My Day over real HTTP + PostgreSQL. Substituted in this test context only: email delivery and
 * the card source (fixture text, standing in for the LLM path, which needs an AI provider).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(MyDayHttpE2eTest.TestSources.class)
class MyDayHttpE2eTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry properties) {
        properties.add("DATABASE_URL", POSTGRES::getJdbcUrl);
        properties.add("DATABASE_USER", POSTGRES::getUsername);
        properties.add("DATABASE_PASSWORD", POSTGRES::getPassword);
        properties.add("MOMNA_REDIS_ENABLED", () -> "false");
        properties.add("MOMNA_OBJECT_STORAGE_ENABLED", () -> "false");
    }

    private static final AtomicReference<String> DELIVERED_CODE = new AtomicReference<>();
    private static final AtomicBoolean SOURCE_DOWN = new AtomicBoolean(false);
    private static final List<MyDayCardSource.Input> SOURCE_CALLS = new java.util.concurrent.CopyOnWriteArrayList<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSources {
        @Bean
        EmailChallengeDelivery testEmailDelivery() {
            return (email, code) -> DELIVERED_CODE.set(code);
        }

        @Bean
        MyDayCardSource fixtureCardSource() {
            return input -> {
                if (SOURCE_DOWN.get()) {
                    throw new MyDayException("MY_DAY_GENERATION_TEMPORARILY_UNAVAILABLE", "Fixture source is down");
                }
                SOURCE_CALLS.add(input);
                var cards = new ArrayList<MyDayCards.Card>();
                for (var slot : MyDayCards.SLOTS) {
                    for (int i = 1; i <= 3; i++) {
                        cards.add(new MyDayCards.Card(slot, i, "Fixture " + slot + " " + i, "microcopy", "what",
                            "why", "how", "if skipped", "support"));
                    }
                }
                return new MyDayCards.GeneratedDay(cards, "TEST_FIXTURE", "myday.generation.v1",
                    "test.prompt.v1", "test-model", "safety.catalog.v1", "fixture:" + input.lifeMode() + ":" + input.language());
            };
        }
    }

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Value("${local.server.port}") int port;
    @Autowired AuthSessionRepository authSessions;
    @Autowired UserProfileRepository userProfiles;
    @Autowired LifecyclePeriodHistoryRepository lifecycleHistory;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.momna.modules.checkin.infrastructure.CheckinSessionRepository checkinSessions;
    @Autowired com.momna.modules.checkin.definition.WeightedCheckinDefinitionCatalog checkinDefinitions;
    @Autowired com.momna.modules.flow.infrastructure.FlowInstanceRepository flowInstances;
    @Autowired com.momna.modules.flow.infrastructure.FlowAnswerRepository flowAnswers;

    private record User(String token, String userId) {}

    @Test
    void baselineDayIsStoredOnceOwnerOnlyAndReadable() throws Exception {
        var user = user(LifecyclePeriod.CYCLE, "myday-baseline");
        var created = post("/api/v1/my-day/generate", user);
        assertEquals(201, created.statusCode(), created.body());
        var day = json.readTree(created.body());
        assertEquals(9, day.path("cards").size());
        var slots = new ArrayList<String>();
        day.path("cards").forEach(c -> slots.add(c.path("slot").asText() + c.path("position").asInt()));
        assertEquals(List.of("AM1", "AM2", "AM3", "PM1", "PM2", "PM3", "OPTIONAL1", "OPTIONAL2", "OPTIONAL3"), slots);
        assertEquals("am-1", day.path("cards").get(0).path("cardId").asText());
        assertEquals("what", day.path("cards").get(0).path("details").path("whatToDo").asText());
        assertEquals("CYCLE", day.path("lifecyclePeriod").asText());
        assertEquals(1.0, day.path("provenance").path("baselineShare").asDouble());
        assertEquals(0, day.path("provenance").path("sources").size());
        assertEquals(9, day.path("progress").path("pending").asInt());
        assertEquals("adult_cycle", SOURCE_CALLS.getLast().lifeMode());

        var again = post("/api/v1/my-day/generate", user);
        assertEquals(200, again.statusCode(), again.body());
        assertEquals(day.path("myDayId").asText(), json.readTree(again.body()).path("myDayId").asText());
        assertEquals(1, jdbc.queryForObject(
            "SELECT count(*) FROM momna.my_days WHERE user_id = ?", Integer.class, user.userId()));

        assertEquals("READY", day.path("status").asText());
        var today = get("/api/v1/my-day/today", user.token());
        assertEquals(200, today.statusCode(), today.body());
        var todayJson = json.readTree(today.body());
        assertEquals("READY", todayJson.path("state").asText());
        assertEquals(day.path("myDayId").asText(), todayJson.path("myDay").path("myDayId").asText());

        // Idempotency-Key: required; a replay returns the same day; reuse for another request conflicts.
        var noKey = send("POST", "/api/v1/my-day/generate", "{}", user, null);
        assertEquals(400, noKey.statusCode(), noKey.body());
        assertEquals("IDEMPOTENCY_KEY_REQUIRED", json.readTree(noKey.body()).path("code").asText());
        String key = "gen-" + UUID.randomUUID();
        var first = send("POST", "/api/v1/my-day/generate", "{\"source\":\"HOME\"}", user, key);
        assertEquals(200, first.statusCode(), first.body());
        var replay = send("POST", "/api/v1/my-day/generate", "{\"source\":\"HOME\"}", user, key);
        assertEquals(200, replay.statusCode(), replay.body());
        assertEquals(day.path("myDayId").asText(), json.readTree(replay.body()).path("myDayId").asText());
        assertEquals("IDEMPOTENCY_CONFLICT", code(send("POST", "/api/v1/my-day/generate",
            "{\"source\":\"RETRY\"}", user, key)));

        var stranger = user(LifecyclePeriod.CYCLE, "myday-stranger");
        assertEquals(404, get("/api/v1/my-day/" + day.path("myDayId").asText(), stranger.token()).statusCode());
        var strangerToday = get("/api/v1/my-day/today", stranger.token());
        assertEquals(200, strangerToday.statusCode(), strangerToday.body());
        assertEquals("NOT_GENERATED", json.readTree(strangerToday.body()).path("state").asText());
        assertTrue(json.readTree(strangerToday.body()).path("myDay").isNull());
        assertEquals(401, get("/api/v1/my-day/" + day.path("myDayId").asText(), null).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM momna.transactional_outbox " +
            "WHERE event_type = 'MyDayGenerated' AND payload->>'myDayId' = ?", Integer.class, day.path("myDayId").asText()));
        var payload = jdbc.queryForObject("SELECT payload::text FROM momna.transactional_outbox " +
            "WHERE event_type = 'MyDayGenerated' AND payload->>'myDayId' = ?", String.class, day.path("myDayId").asText());
        assertFalse(payload.contains("Fixture"), "events must not carry card content: " + payload);
    }

    @Test
    void cardProgressIsRevisionedIdempotentAndFinal() throws Exception {
        var user = user(LifecyclePeriod.PREGNANCY, "myday-progress");
        var day = json.readTree(post("/api/v1/my-day/generate", user).body());
        String root = "/api/v1/my-day/" + day.path("myDayId").asText();
        long revision = day.path("revision").asLong();
        String key = "card-" + UUID.randomUUID();
        var done = patch(root + "/cards/am-1", revision, "COMPLETED", user, key);
        assertEquals(200, done.statusCode(), done.body());
        var doneJson = json.readTree(done.body());
        assertEquals("COMPLETED", doneJson.path("cards").get(0).path("status").asText());
        assertEquals(1, doneJson.path("progress").path("completed").asInt());
        assertTrue(doneJson.path("revision").asLong() > revision);

        var replay = patch(root + "/cards/am-1", revision, "COMPLETED", user, key);
        assertEquals(200, replay.statusCode(), replay.body());
        assertEquals(doneJson.path("revision").asLong(), json.readTree(replay.body()).path("revision").asLong());
        assertEquals("IDEMPOTENCY_CONFLICT", code(patch(root + "/cards/am-1", revision, "SKIPPED", user, key)));
        assertEquals("STALE_REVISION", code(patch(root + "/cards/pm-1", revision, "SKIPPED", user, "k-" + UUID.randomUUID())));
        long current = doneJson.path("revision").asLong();
        assertEquals("MY_DAY_CARD_STATE_CONFLICT", code(patch(root + "/cards/am-1", current, "COMPLETED", user, "k-" + UUID.randomUUID())));
        assertEquals("MY_DAY_CARD_STATE_CONFLICT", code(patch(root + "/cards/pm-1", current, "PENDING", user, "k-" + UUID.randomUUID())));
        var invalid = patch(root + "/cards/pm-1", current, "DONE", user, "k-" + UUID.randomUUID());
        assertEquals(422, invalid.statusCode(), invalid.body());
        var unknown = patch(root + "/cards/zz-9", current, "SKIPPED", user, "k-" + UUID.randomUUID());
        assertEquals(404, unknown.statusCode(), unknown.body());

        var skipped = patch(root + "/cards/optional-2", current, "SKIPPED", user, "k-" + UUID.randomUUID());
        assertEquals(200, skipped.statusCode(), skipped.body());
        var progress = json.readTree(skipped.body()).path("progress");
        assertEquals(1, progress.path("completed").asInt());
        assertEquals(1, progress.path("skipped").asInt());
        assertEquals(7, progress.path("pending").asInt());

        // Progress is durable: a later generate returns the same day with its progress.
        var later = json.readTree(post("/api/v1/my-day/generate", user).body());
        assertEquals(day.path("myDayId").asText(), later.path("myDayId").asText());
        assertEquals(2, later.path("progress").path("completed").asInt() + later.path("progress").path("skipped").asInt());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM momna.transactional_outbox " +
            "WHERE event_type = 'MyDayCardCompleted' AND payload->>'myDayId' = ?", Integer.class, day.path("myDayId").asText()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM momna.transactional_outbox " +
            "WHERE event_type = 'MyDayCardSkipped' AND payload->>'myDayId' = ?", Integer.class, day.path("myDayId").asText()));

        // "Mark as Not Completed" (design D-7): a mark can be taken back and given again.
        long revisionNow = json.readTree(skipped.body()).path("revision").asLong();
        var reopened = patch(root + "/cards/am-1", revisionNow, "PENDING", user, "k-" + UUID.randomUUID());
        assertEquals(200, reopened.statusCode(), reopened.body());
        var reopenedCard = json.readTree(reopened.body()).path("cards").get(0);
        assertEquals("PENDING", reopenedCard.path("status").asText());
        assertTrue(reopenedCard.path("statusChangedAt").isNull());
        revisionNow = json.readTree(reopened.body()).path("revision").asLong();
        var redone = patch(root + "/cards/am-1", revisionNow, "COMPLETED", user, "k-" + UUID.randomUUID());
        assertEquals(200, redone.statusCode(), redone.body());
        revisionNow = json.readTree(redone.body()).path("revision").asLong();
        assertEquals(2, outboxCount("MyDayCardCompleted", day.path("myDayId").asText()));
        assertEquals(1, outboxCount("MyDayCardReopened", day.path("myDayId").asText()));

        // MyDayCompleted: once, when every AM and PM card is COMPLETED; optional cards do not count.
        for (var cardId : List.of("am-2", "am-3", "pm-1", "pm-2", "pm-3")) {
            assertEquals(0, outboxCount("MyDayCompleted", day.path("myDayId").asText()), "completed before " + cardId);
            var updated = patch(root + "/cards/" + cardId, revisionNow, "COMPLETED", user, "k-" + UUID.randomUUID());
            assertEquals(200, updated.statusCode(), updated.body());
            revisionNow = json.readTree(updated.body()).path("revision").asLong();
        }
        var completion = jdbc.queryForList("SELECT payload::text AS p FROM momna.transactional_outbox " +
            "WHERE event_type = 'MyDayCompleted' AND payload->>'myDayId' = ?", day.path("myDayId").asText());
        assertEquals(1, completion.size());
        var completionPayload = json.readTree((String) completion.get(0).get("p"));
        assertEquals(6, completionPayload.path("completedCount").asInt());
        assertFalse(completion.get(0).get("p").toString().contains("Fixture"));
        // Taking a mark back and giving it again never completes the day a second time.
        var undo = patch(root + "/cards/pm-3", revisionNow, "PENDING", user, "k-" + UUID.randomUUID());
        assertEquals(200, undo.statusCode(), undo.body());
        var again = patch(root + "/cards/pm-3", json.readTree(undo.body()).path("revision").asLong(), "COMPLETED",
            user, "k-" + UUID.randomUUID());
        assertEquals(200, again.statusCode(), again.body());
        revisionNow = json.readTree(again.body()).path("revision").asLong();
        var optional = patch(root + "/cards/optional-1", revisionNow, "COMPLETED", user, "k-" + UUID.randomUUID());
        assertEquals(200, optional.statusCode(), optional.body());
        assertEquals(1, outboxCount("MyDayCompleted", day.path("myDayId").asText()));

        var stranger = user(LifecyclePeriod.PREGNANCY, "myday-progress-stranger");
        var foreign = patch(root + "/cards/pm-1", current, "SKIPPED", stranger, "k-" + UUID.randomUUID());
        assertEquals(404, foreign.statusCode(), foreign.body());
    }

    @Test
    void submittedMorningCheckinFeedsTheDayAndIsConsumedOnlyAfterItIsStored() throws Exception {
                var user = user(LifecyclePeriod.PLANNING, "myday-morning");
        var session = json.readTree(postWithKey("/api/v1/check-in/sessions",
            json.createObjectNode().put("phase", "MORNING").toString(), user, "s-" + UUID.randomUUID()).body());
        String endpoint = "/api/v1/check-in/sessions/" + session.path("sessionId").asText();
        var change = json.createObjectNode().put("expectedRevision", session.path("revision").asLong());
        change.putArray("answerChanges").addObject()
            .put("itemCode", session.path("items").get(0).path("itemCode").asText()).put("value", 1);
        var patched = json.readTree(send("PATCH", endpoint, change.toString(), user, "p-" + UUID.randomUUID()).body());
        var submitted = postWithKey(endpoint + "/submit", json.createObjectNode()
            .put("expectedRevision", patched.path("revision").asLong()).toString(), user, "sub-" + UUID.randomUUID());
        assertEquals(200, submitted.statusCode(), submitted.body());

        // The source is down: nothing is stored and the Check-in is not consumed.
        SOURCE_DOWN.set(true);
        try {
            var failed = post("/api/v1/my-day/generate", user);
            assertEquals(503, failed.statusCode(), failed.body());
            assertEquals("MY_DAY_GENERATION_TEMPORARILY_UNAVAILABLE", json.readTree(failed.body()).path("code").asText());
            assertEquals("30", failed.headers().firstValue("Retry-After").orElse(null));
        } finally {
            SOURCE_DOWN.set(false);
        }
        assertNull(jdbc.queryForObject("SELECT consumed_by_ref FROM momna.checkin_sessions WHERE session_id = ?",
            String.class, session.path("sessionId").asText()));
        assertEquals(0, jdbc.queryForObject(
            "SELECT count(*) FROM momna.my_days WHERE user_id = ?", Integer.class, user.userId()));

        var created = post("/api/v1/my-day/generate", user);
        assertEquals(201, created.statusCode(), created.body());
        var day = json.readTree(created.body());
        var sources = day.path("provenance").path("sources");
        assertEquals(1, sources.size(), day.path("provenance").toString());
        assertEquals("MORNING", sources.get(0).path("phase").asText());
        assertEquals(day.path("localDate").asText(), sources.get(0).path("sourceLocalDate").asText());
        assertEquals("SUBMITTED", sources.get(0).path("state").asText());
        assertTrue(day.path("provenance").path("baselineShare").asDouble() < 1.0);
        assertFalse(sources.get(0).has("sourceRef"), "session ids are not part of the public provenance");
        assertEquals("PERSONAL", day.path("kind").asText());
        assertEquals("Personal", day.path("badge").asText());
        assertEquals(day.path("myDayId").asText(), jdbc.queryForObject(
            "SELECT consumed_by_ref FROM momna.checkin_sessions WHERE session_id = ?",
            String.class, session.path("sessionId").asText()));
    }

    /**
     * MOMNA-1324/1325: the onboarding answers and the weighted Check-in decide the overlay keys once, when
     * the day is made; only the anonymous template key reaches the card source, and the public API shows
     * neither the key nor the overlays.
     */
    @Test
    void theTemplateKeyCarriesTheOverlaysOfTheAnswersAndNothingPersonal() throws Exception {
        var user = user(LifecyclePeriod.CYCLE, "myday-template");
        var zone = java.time.ZoneId.of(userProfiles.findById(user.userId()).orElseThrow().getTimezone());
        var today = java.time.LocalDate.now(zone);
        completedOnboarding(user, "CYCLE", "period-onboarding-cycle", java.util.Map.of(
            "onboarding.cycle.lmp", today.minusDays(1).toString(),
            "onboarding.cycle.regularity", "around_expected",
            "onboarding.cycle.tone", "direct"));

        var session = json.readTree(postWithKey("/api/v1/check-in/sessions",
            json.createObjectNode().put("phase", "MORNING").toString(), user, "s-" + UUID.randomUUID()).body());
        String endpoint = "/api/v1/check-in/sessions/" + session.path("sessionId").asText();
        var change = json.createObjectNode().put("expectedRevision", session.path("revision").asLong());
        var answers = change.putArray("answerChanges");
        answers.addObject().put("itemCode", "C3").put("value", 2);   // pain: noticeable
        answers.addObject().put("itemCode", "C4").put("value", 1);   // flow: only mildly heavier
        var patched = send("PATCH", endpoint, change.toString(), user, "p-" + UUID.randomUUID());
        assertEquals(200, patched.statusCode(), patched.body());
        var submitted = postWithKey(endpoint + "/submit", json.createObjectNode()
            .put("expectedRevision", json.readTree(patched.body()).path("revision").asLong()).toString(), user,
            "sub-" + UUID.randomUUID());
        assertEquals(200, submitted.statusCode(), submitted.body());

        var created = post("/api/v1/my-day/generate", user);
        assertEquals(201, created.statusCode(), created.body());
        var call = SOURCE_CALLS.stream().filter(c -> user.userId().equals(c.userId())).reduce((a, b) -> b).orElseThrow();
        var key = call.templateKey();
        assertEquals("adult_cycle", key.lifeMode());
        assertEquals("menstrual", key.cycleState());
        assertEquals("adult_menstrual", key.phaseCode());
        assertEquals(2, key.dayNumber());
        assertEquals(List.of("adult_pain_high"), key.overlayKeys(), "one mild answer does not make an overlay");
        assertEquals("en", key.userLanguage());
        assertEquals("direct", key.tone(), "the onboarding tone is a prompt input and part of the key");
        assertFalse(key.canonical().contains(user.userId()));
        assertFalse(key.canonical().contains(session.path("sessionId").asText()));

        var day = json.readTree(created.body());
        assertFalse(day.path("provenance").has("template"), "the client never sees the key or the overlays");
        assertFalse(created.body().contains("adult_pain_high"));
        assertTrue(day.path("healthNote").isNull(), "an ordinary day has no doctor advice: " + day.path("healthNote"));
        assertEquals(key.fingerprint(), jdbc.queryForObject(
            "SELECT provenance->'template'->>'fingerprint' FROM momna.my_days WHERE my_day_id = ?", String.class,
            day.path("myDayId").asText()));
    }

    /**
     * ER0 (MOMNA-1145 §8) over HTTP: the period's signs, one tap to the emergency screen, idempotent per key,
     * and the outcome reaches My Day as the health overlay with the disclaimer and no doctor line (the
     * emergency screen already shows it).
     */
    @Test
    void er0IsOneTapIdempotentAndReachesMyDay() throws Exception {
        var user = user(LifecyclePeriod.CYCLE, "safety-er0");
        var screen = json.readTree(get("/api/v1/medical-safety/er0", user.token()).body());
        assertEquals("Что происходит прямо сейчас?", screen.path("title").path("ru").asText(), screen.toString());
        assertEquals(10, screen.path("options").size());

        String key = "er0-" + UUID.randomUUID();
        var tapped = postWithKey("/api/v1/medical-safety/er0",
            json.createObjectNode().put("selectionCode", "F_CHEST_PAIN").toString(), user, key);
        assertEquals(200, tapped.statusCode(), tapped.body());
        var step = json.readTree(tapped.body()).path("step");
        assertEquals("EMERGENCY", step.path("disposition").asText(), step.toString());
        assertEquals("E", step.path("screenCode").asText());
        assertEquals("CALL_EMERGENCY", step.path("actionCode").asText());
        assertTrue(json.readTree(tapped.body()).path("checkinSessionId").isNull());

        var replay = postWithKey("/api/v1/medical-safety/er0",
            json.createObjectNode().put("selectionCode", "F_CHEST_PAIN").toString(), user, key);
        assertEquals(200, replay.statusCode(), replay.body());
        assertEquals(json.readTree(tapped.body()), json.readTree(replay.body()));
        var reused = postWithKey("/api/v1/medical-safety/er0",
            json.createObjectNode().put("selectionCode", "F_FAINTED").toString(), user, key);
        assertEquals(409, reused.statusCode(), reused.body());
        assertEquals("IDEMPOTENCY_CONFLICT", code(reused));
        var notShown = postWithKey("/api/v1/medical-safety/er0",
            json.createObjectNode().put("selectionCode", "F_FLUID_LEAK").toString(), user, "er0-" + UUID.randomUUID());
        assertEquals(422, notShown.statusCode(), notShown.body());
        assertEquals("SAFETY_SIGNAL_NOT_APPLICABLE", code(notShown));

        var created = post("/api/v1/my-day/generate", user);
        assertEquals(201, created.statusCode(), created.body());
        var call = SOURCE_CALLS.stream().filter(c -> user.userId().equals(c.userId())).reduce((a, b) -> b).orElseThrow();
        assertTrue(call.templateKey().overlayKeys().contains("adult_health_flag"), call.templateKey().overlayKeys().toString());
        var note = json.readTree(created.body()).path("healthNote");
        assertEquals("EMERGENCY", note.path("level").asText(), note.toString());
        assertTrue(note.path("recommendation").isNull(), "the emergency screen has the advice; My Day does not repeat it");
        assertFalse(note.path("disclaimer").asText().isBlank());
    }

    /**
     * WB1 (MOMNA-1145 §9.4) over HTTP: shown once about four weeks after birth; "it's been hard lately" opens
     * R18 with harm first; a low mood answer is a PROMPT, and My Day gets post_mood_heavy with the doctor line.
     */
    @Test
    void wb1OpensR18AndReachesMyDay() throws Exception {
        var user = user(LifecyclePeriod.POSTPARTUM, "safety-wb1");
        var zone = java.time.ZoneId.of(userProfiles.findById(user.userId()).orElseThrow().getTimezone());
        completedOnboarding(user, "POSTPARTUM", "period-onboarding-postpartum", 6, java.util.Map.of(
            "onboarding.postpartum.birth_date", java.time.LocalDate.now(zone).minusDays(30).toString()));
        var card = json.readTree(get("/api/v1/medical-safety/wb1", user.token()).body());
        assertTrue(card.path("show").asBoolean(), card.toString());
        assertEquals("Как вы чувствуете себя эмоционально в последнее время?",
            card.path("screen").path("fields").path("title").path("ru").asText());

        var hard = postWithKey("/api/v1/medical-safety/wb1",
            json.createObjectNode().put("answer", "HARD_LATELY").toString(), user, "wb1-" + UUID.randomUUID());
        assertEquals(200, hard.statusCode(), hard.body());
        var step = json.readTree(hard.body()).path("step");
        assertEquals("R18", step.path("routeId").asText(), step.toString());
        assertEquals("F_HARM_THOUGHTS", step.path("nextFactCode").asText());
        assertFalse(json.readTree(get("/api/v1/medical-safety/wb1", user.token()).body()).path("show").asBoolean());

        var harm = postWithKey("/api/v1/medical-safety/routes/R18/answers", json.createObjectNode()
            .put("factCode", "F_HARM_THOUGHTS").put("answer", "NO").toString(), user, "r18-" + UUID.randomUUID());
        assertEquals(200, harm.statusCode(), harm.body());
        var next = json.readTree(harm.body()).path("step").path("nextFactCode").asText();
        var mood = postWithKey("/api/v1/medical-safety/routes/R18/answers", json.createObjectNode()
            .put("factCode", next).put("answer", "YES").toString(), user, "r18-" + UUID.randomUUID());
        assertEquals(200, mood.statusCode(), mood.body());
        var outcome = json.readTree(mood.body()).path("step");
        assertEquals("PROMPT", outcome.path("disposition").asText(), outcome.toString());
        assertEquals("PM1", outcome.path("screenCode").asText());

        var created = post("/api/v1/my-day/generate", user);
        assertEquals(201, created.statusCode(), created.body());
        var call = SOURCE_CALLS.stream().filter(c -> user.userId().equals(c.userId())).reduce((a, b) -> b).orElseThrow();
        assertTrue(call.templateKey().overlayKeys().contains("post_mood_heavy"), call.templateKey().overlayKeys().toString());
        var note = json.readTree(created.body()).path("healthNote");
        assertEquals("PROMPT", note.path("level").asText(), note.toString());
        assertEquals("Please discuss this symptom with a healthcare professional.", note.path("recommendation").asText());
    }

    /** WB1 «It's been hard lately» for a fresh postpartum user, returning the R18 harm question. */
    private User wb1HardLately(String label) throws Exception {
        var user = user(LifecyclePeriod.POSTPARTUM, label);
        var zone = java.time.ZoneId.of(userProfiles.findById(user.userId()).orElseThrow().getTimezone());
        completedOnboarding(user, "POSTPARTUM", "period-onboarding-postpartum", 6, java.util.Map.of(
            "onboarding.postpartum.birth_date", java.time.LocalDate.now(zone).minusDays(30).toString()));
        var hard = postWithKey("/api/v1/medical-safety/wb1",
            json.createObjectNode().put("answer", "HARD_LATELY").toString(), user, "wb1-" + UUID.randomUUID());
        assertEquals(200, hard.statusCode(), hard.body());
        assertEquals("F_HARM_THOUGHTS", json.readTree(hard.body()).path("step").path("nextFactCode").asText());
        return user;
    }

    private Instant r18PausedUntil(User user) {
        return jdbc.queryForObject("SELECT cooldown_until FROM momna.medical_safety_route_states "
            + "WHERE user_id = ? AND route_id = 'R18'", java.sql.Timestamp.class, user.userId()).toInstant();
    }

    /**
     * Catalog v2.3 (MOMNA-1146, Product 28.11.2026): «Not sure» on the harm question continues; with nothing
     * higher it ends on PM1-H (PM1 plus the urgent-help link), R18 pauses 7 days, and My Day gets the support
     * signal with the PM1 doctor line.
     */
    @Test
    void notSureOnTheHarmQuestionEndsOnThePm1VariantAndReachesMyDay() throws Exception {
        var user = wb1HardLately("safety-harm-unsure");
        var step = json.readTree(postWithKey("/api/v1/medical-safety/routes/R18/answers", json.createObjectNode()
            .put("factCode", "F_HARM_THOUGHTS").put("answer", "UNSURE").toString(), user, "r18-" + UUID.randomUUID())
            .body()).path("step");
        assertEquals("F_LOW_MOOD_2W", step.path("nextFactCode").asText(), step.toString());
        for (int n = 0; step.path("nextFactCode").isTextual() && !step.path("nextFactCode").asText().isEmpty() && n < 5; n++) {
            var answered = postWithKey("/api/v1/medical-safety/routes/R18/answers", json.createObjectNode()
                .put("factCode", step.path("nextFactCode").asText()).put("answer", "NO").toString(), user,
                "r18-" + UUID.randomUUID());
            assertEquals(200, answered.statusCode(), answered.body());
            step = json.readTree(answered.body()).path("step");
        }
        assertEquals("PROMPT", step.path("disposition").asText(), step.toString());
        assertEquals("PM1-H", step.path("screenCode").asText());
        var pause = Duration.between(Instant.now(), r18PausedUntil(user)).toHours();
        assertTrue(pause >= 166 && pause <= 168, "R18 pauses 7 days: " + pause);

        var catalog = json.readTree(get("/api/v1/medical-safety/catalog", user.token()).body());
        var variant = java.util.stream.StreamSupport.stream(catalog.path("localizedContent").path("screens").spliterator(), false)
            .filter(s -> "PM1-H".equals(s.path("code").asText())).findFirst().orElseThrow();
        assertEquals("Not feeling safe right now? How to get urgent help",
            variant.path("fields").path("urgentHelpLink").path("enUs").asText());

        var created = post("/api/v1/my-day/generate", user);
        assertEquals(201, created.statusCode(), created.body());
        var call = SOURCE_CALLS.stream().filter(c -> user.userId().equals(c.userId())).reduce((a, b) -> b).orElseThrow();
        assertTrue(call.templateKey().overlayKeys().contains("post_mood_heavy"), call.templateKey().overlayKeys().toString());
        var note = json.readTree(created.body()).path("healthNote");
        assertEquals("PROMPT", note.path("level").asText(), note.toString());
        assertEquals("Please discuss this symptom with a healthcare professional.", note.path("recommendation").asText());
    }

    /**
     * Catalog v2.3: «Not now» on the harm question is «not answered» — H1, a 3-day pause, and nothing for My Day.
     */
    @Test
    void notNowOnTheHarmQuestionIsNotAnsweredAndMyDayGetsNothing() throws Exception {
        var user = wb1HardLately("safety-harm-not-now");
        var key = "r18-skip-" + UUID.randomUUID();
        var skipped = postWithKey("/api/v1/medical-safety/routes/R18/skip", "{}", user, key);
        assertEquals(200, skipped.statusCode(), skipped.body());
        var step = json.readTree(skipped.body()).path("step");
        assertEquals("NOT_ANSWERED", step.path("disposition").asText(), step.toString());
        assertEquals("H1", step.path("screenCode").asText());
        assertEquals(skipped.body(), postWithKey("/api/v1/medical-safety/routes/R18/skip", "{}", user, key).body(),
            "an Idempotency-Key replay returns the stored response");
        var pause = Duration.between(Instant.now(), r18PausedUntil(user)).toHours();
        assertTrue(pause >= 70 && pause <= 72, "R18 pauses 3 days: " + pause);

        var created = post("/api/v1/my-day/generate", user);
        assertEquals(201, created.statusCode(), created.body());
        var call = SOURCE_CALLS.stream().filter(c -> user.userId().equals(c.userId())).reduce((a, b) -> b).orElseThrow();
        assertFalse(call.templateKey().overlayKeys().contains("post_mood_heavy"), call.templateKey().overlayKeys().toString());
        assertTrue(json.readTree(created.body()).path("healthNote").isNull()
            || json.readTree(created.body()).path("healthNote").isMissingNode(), created.body());
    }

    /** A calendar signal (MOMNA-1145 §12) decides without daily questions and only where it applies. */
    @Test
    void aCalendarSignalDecidesInItsPeriodOnly() throws Exception {
        var user = user(LifecyclePeriod.CYCLE, "safety-calendar");
        var signal = postWithKey("/api/v1/medical-safety/calendar/signals",
            json.createObjectNode().put("signal", "POSTCOITAL_BLEED").toString(), user, "cal-" + UUID.randomUUID());
        assertEquals(200, signal.statusCode(), signal.body());
        var step = json.readTree(signal.body()).path("step");
        assertEquals("R4", step.path("routeId").asText(), step.toString());
        assertEquals("PROMPT", step.path("disposition").asText());
        var wrong = postWithKey("/api/v1/medical-safety/calendar/signals",
            json.createObjectNode().put("signal", "BLEEDING_RECORDED").toString(), user, "cal-" + UUID.randomUUID());
        assertEquals(422, wrong.statusCode(), wrong.body());
        var catalog = json.readTree(get("/api/v1/medical-safety/catalog", user.token()).body());
        assertEquals("CLINICAL_RELEASE_NOT_APPROVED", catalog.path("releaseStatus").asText(), catalog.toString());
        assertEquals(28, catalog.path("routes").size());
        assertFalse(catalog.toString().contains("crossChecks"));
        assertEquals(22, catalog.path("localizedContent").path("screens").size(), catalog.path("localizedContent").toString());
    }

    /**
     * The whole chain (MOMNA-1145, Product decision 25.11.2026): a Check-in safety route decides; My Day reads
     * that decision. An urgent bleeding sign answered "unsure" ends UNKNOWN and gives the day the health overlay,
     * the UNKNOWN doctor line and the disclaimer; the same route answered "no" gives neither.
     */
    @Test
    void aCheckinSafetyOutcomeReachesMyDayAsTheDoctorLine() throws Exception {
        var unsure = safetyDay("myday-safety-unsure", "UNSURE");
        assertEquals(List.of("adult_health_flag"), unsure.key().overlayKeys().stream()
            .filter(k -> k.endsWith("_health_flag")).toList(), unsure.key().overlayKeys().toString());
        var note = unsure.day().path("healthNote");
        assertEquals("UNKNOWN", note.path("level").asText(), note.toString());
        assertEquals("Your answers do not establish urgency reliably. Seek medical help if symptoms worsen or "
            + "warning signs appear.", note.path("recommendation").asText());
        assertEquals("Momna offers guidance, not a diagnosis, and doesn't replace your doctor.",
            note.path("disclaimer").asText());

        var no = safetyDay("myday-safety-no", "NO");
        assertTrue(no.key().overlayKeys().stream().noneMatch(k -> k.endsWith("_health_flag")),
            no.key().overlayKeys().toString());
        assertTrue(no.day().path("healthNote").isNull(), "no doctor advice for a route with nothing found");
    }

    private record SafetyDay(com.momna.modules.myday.application.template.MyDayTemplateKey key,
        com.fasterxml.jackson.databind.JsonNode day) {}

    /** A CYCLE morning Check-in with C4 = 2 (R4 CYCLE_BLEEDING) whose questions get one answer, then My Day. */
    private SafetyDay safetyDay(String label, String answer) throws Exception {
        var user = user(LifecyclePeriod.CYCLE, label);
        var session = json.readTree(postWithKey("/api/v1/check-in/sessions",
            json.createObjectNode().put("phase", "MORNING").toString(), user, "s-" + UUID.randomUUID()).body());
        String endpoint = "/api/v1/check-in/sessions/" + session.path("sessionId").asText();
        var change = json.createObjectNode().put("expectedRevision", session.path("revision").asLong());
        change.putArray("answerChanges").addObject().put("itemCode", "C4").put("value", 2);
        var state = json.readTree(send("PATCH", endpoint, change.toString(), user, "p-" + UUID.randomUUID()).body());
        for (int guard = 0; guard < 10 && state.path("safety").path("blocking").asBoolean(); guard++) {
            var r4 = state.path("safety").path("routes").get(0);
            assertEquals("R4", r4.path("routeId").asText(), state.path("safety").toString());
            var answered = postWithKey(endpoint + "/safety/R4/answers", json.createObjectNode()
                .put("expectedRevision", state.path("revision").asLong())
                .put("factCode", r4.path("nextFactCode").asText()).put("answer", answer).toString(), user,
                "a-" + UUID.randomUUID());
            assertEquals(200, answered.statusCode(), answered.body());
            state = json.readTree(answered.body());
        }
        assertEquals("CYCLE_BLEEDING", state.path("safety").path("routeCode").asText(), state.path("safety").toString());
        assertEquals("UNSURE".equals(answer) ? "UNKNOWN" : "NONE", state.path("safety").path("severity").asText());
        var submitted = postWithKey(endpoint + "/submit", json.createObjectNode()
            .put("expectedRevision", state.path("revision").asLong()).toString(), user, "sub-" + UUID.randomUUID());
        assertEquals(200, submitted.statusCode(), submitted.body());

        var created = post("/api/v1/my-day/generate", user);
        assertEquals(201, created.statusCode(), created.body());
        var call = SOURCE_CALLS.stream().filter(c -> user.userId().equals(c.userId())).reduce((a, b) -> b).orElseThrow();
        return new SafetyDay(call.templateKey(), json.readTree(created.body()));
    }

    /**
     * Product decision 25.11.2026: a day with a medical overlay carries the code-owned doctor line of its
     * level and the disclaimer, in the day's language; URGENT and EMERGENCY add no line (the urgent path has it).
     */
    @Test
    void aDayWithAMedicalOverlayCarriesTheDoctorLineAndTheDisclaimer() throws Exception {
        var user = user(LifecyclePeriod.CYCLE, "myday-health-note");
        var created = json.readTree(post("/api/v1/my-day/generate", user).body());
        var myDayId = created.path("myDayId").asText();
        assertTrue(created.path("healthNote").isNull(), created.toString());

        jdbc.update("UPDATE momna.my_days SET provenance = jsonb_set(provenance, '{template}', "
            + "coalesce(provenance->'template', '{}'::jsonb) || '{\"healthLevel\": \"PROMPT\"}'::jsonb) "
            + "WHERE my_day_id = ?", myDayId);
        var note = json.readTree(get("/api/v1/my-day/today", user.token()).body()).path("myDay").path("healthNote");
        assertEquals("PROMPT", note.path("level").asText(), note.toString());
        assertEquals("Please discuss this symptom with a healthcare professional.", note.path("recommendation").asText());
        assertEquals("Momna offers guidance, not a diagnosis, and doesn't replace your doctor.",
            note.path("disclaimer").asText());

        jdbc.update("UPDATE momna.my_days SET provenance = jsonb_set(provenance, '{template,healthLevel}', "
            + "'\"URGENT\"'::jsonb) WHERE my_day_id = ?", myDayId);
        var urgent = json.readTree(get("/api/v1/my-day/today", user.token()).body()).path("myDay").path("healthNote");
        assertEquals("URGENT", urgent.path("level").asText(), urgent.toString());
        assertTrue(urgent.path("recommendation").isNull(), "the urgent path shows it; My Day does not repeat it");
        assertFalse(urgent.path("disclaimer").asText().isBlank());
    }

    /**
     * Texts of the implemented design (Adult Cycle export of 24.11.2026): the My Day subtitle, the
     * Standard card, screen L, and the Check-in tab cards before and after the morning Check-in.
     */
    @Test
    void theScreensCarryTheDesignTexts() throws Exception {
        var user = user(LifecyclePeriod.CYCLE, "myday-design-texts");
        var today = json.readTree(get("/api/v1/my-day/today", user.token()).body());
        assertEquals("Recommendations for a regular cycle.", today.path("subtitle").asText());
        assertEquals("Standard", today.path("planChoice").path("standard").path("title").asText());
        assertEquals("General recommendations for a regular cycle — no questions.",
            today.path("planChoice").path("standard").path("description").asText());
        assertEquals("Getting your plan ready", today.path("generating").path("title").asText());
        assertEquals("I'm turning your answers into today's recommendations.",
            today.path("generating").path("description").asText());

        var checkin = json.readTree(get("/api/v1/check-in/today", user.token()).body());
        assertEquals("How you feel, turned into a plan.", checkin.path("subtitle").asText());
        var morning = checkin.path("phases").get(0);
        assertEquals("MORNING", morning.path("phase").asText());
        assertEquals("How are you today?", morning.path("card").path("title").asText());
        assertEquals("A few quick questions about mood, energy, and body — I'll shape your day around the answers.",
            morning.path("card").path("body").asText());
        assertEquals("Morning Check-in", morning.path("sheet").path("title").asText(), morning.toString());
        assertEquals("Tell me how you feel — I'll handle today.", morning.path("sheet").path("subtitle").asText());
        assertEquals(1, morning.path("minAnswersToApply").asInt());

        var session = json.readTree(postWithKey("/api/v1/check-in/sessions",
            json.createObjectNode().put("phase", "MORNING").toString(), user, "s-" + UUID.randomUUID()).body());
        var c8 = session.path("items").get(7);
        assertEquals("C8", c8.path("itemCode").asText(), session.path("items").toString());
        assertEquals("Day's workload", c8.path("title").asText());
        assertEquals("[{\"value\":0,\"text\":\"Light day\",\"tone\":\"GOOD\"},{\"value\":1,\"text\":\"Ordinary\","
            + "\"tone\":\"NEUTRAL\"},{\"value\":2,\"text\":\"Very packed\",\"tone\":\"NOT_GOOD\"}]",
            c8.path("options").toString());
        String endpoint = "/api/v1/check-in/sessions/" + session.path("sessionId").asText();
        var change = json.createObjectNode().put("expectedRevision", session.path("revision").asLong());
        change.putArray("answerChanges").addObject().put("itemCode", "C1").put("value", 0);
        var patched = json.readTree(send("PATCH", endpoint, change.toString(), user, "p-" + UUID.randomUUID()).body());
        assertEquals(200, postWithKey(endpoint + "/submit", json.createObjectNode()
            .put("expectedRevision", patched.path("revision").asLong()).toString(), user, "sub-" + UUID.randomUUID())
            .statusCode());
        var after = json.readTree(get("/api/v1/check-in/today", user.token()).body()).path("phases").get(0);
        assertEquals("VIEW_FINAL_STATE", after.path("allowedEntryAction").asText());
        assertEquals("Your personal plan is ready", after.path("card").path("title").asText());
        assertEquals("Today is tailored to how you feel.", after.path("card").path("body").asText());
        assertEquals("Completed", after.path("card").path("statusLabel").asText());

        var day = json.readTree(post("/api/v1/my-day/generate", user).body()).path("myDay");
        if (day.isMissingNode()) day = json.readTree(get("/api/v1/my-day/today", user.token()).body()).path("myDay");
        assertEquals("TODAY'S PLAN", day.path("planHeader").asText(), day.toString());
        var parts = new java.util.ArrayList<String>();
        for (var group : day.path("groups")) parts.add(group.path("part").asText());
        assertEquals(List.of("MORNING", "EVENING", "OPTIONAL"), parts);
        assertEquals("Morning", day.path("groups").get(0).path("title").asText());
        assertEquals(3, day.path("groups").get(0).path("cardIds").size());
        assertEquals("There is nothing in your plan for today yet.", day.path("emptyPlanText").asText());
        assertTrue(day.path("upgradeOffer").isNull(), "one generation per day: no upgrade offer");
    }

    @Test
    void whenGenerationIsDownAnotherUsersDayIsNeverServed() throws Exception {
        var first = user(LifecyclePeriod.MENOPAUSE, "myday-other-user");
        assertEquals(201, post("/api/v1/my-day/generate", first).statusCode());
        SOURCE_DOWN.set(true);
        try {
            var second = user(LifecyclePeriod.MENOPAUSE, "myday-no-shared-fallback");
            var failed = post("/api/v1/my-day/generate", second);
            assertEquals(503, failed.statusCode(), failed.body());
            assertEquals("MY_DAY_GENERATION_TEMPORARILY_UNAVAILABLE", json.readTree(failed.body()).path("code").asText());
            assertEquals("30", failed.headers().firstValue("Retry-After").orElse(null));
            assertEquals("NOT_GENERATED", json.readTree(get("/api/v1/my-day/today", second.token()).body())
                .path("state").asText());
            assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM momna.my_days WHERE user_id = ?", Integer.class, second.userId()));
        } finally {
            SOURCE_DOWN.set(false);
        }
    }

    /**
     * One generation per day (MOMNA-989, 23.11.2026): once the plan exists, the morning Check-in is
     * closed until the evening with Product's text, and a draft started earlier cannot join the day.
     */
    @Test
    void afterThePlanTheMorningCheckinIsClosedWithTheProductText() throws Exception {
        var user = user(LifecyclePeriod.CYCLE, "myday-morning-closed");
        var draft = json.readTree(postWithKey("/api/v1/check-in/sessions",
            json.createObjectNode().put("phase", "MORNING").toString(), user, "s-" + UUID.randomUUID()).body());
        var open = morningSummary(user);
        assertEquals("RESUME", open.path("allowedEntryAction").asText(), open.toString());
        assertTrue(open.path("entryBlockedReason").isNull(), open.toString());

        assertEquals(201, post("/api/v1/my-day/generate", user).statusCode());

        var closed = morningSummary(user);
        assertEquals("NONE", closed.path("allowedEntryAction").asText(), closed.toString());
        assertEquals("MY_DAY_PLAN_ALREADY_SET", closed.path("entryBlockedReason").asText());
        assertEquals("Today's plan is already set. You can check in this evening, and every morning before your plan is made.",
            closed.path("entryBlockedText").asText());
        var profile = userProfiles.findById(user.userId()).orElseThrow();
        profile.updateLocalization("ru", "ru-RU", "RU", profile.getTimezone(), Instant.now());
        userProfiles.saveAndFlush(profile);
        assertEquals("План на сегодня уже составлен. Вы можете пройти чек-ин сегодня вечером и каждый день утром до составления плана дня.",
            morningSummary(user).path("entryBlockedText").asText());

        var start = postWithKey("/api/v1/check-in/sessions",
            json.createObjectNode().put("phase", "MORNING").toString(), user, "s-" + UUID.randomUUID());
        assertEquals(409, start.statusCode(), start.body());
        assertEquals("CHECKIN_MORNING_PLAN_ALREADY_SET", code(start));
        var submit = postWithKey("/api/v1/check-in/sessions/" + draft.path("sessionId").asText() + "/submit",
            json.createObjectNode().put("expectedRevision", draft.path("revision").asLong()).toString(),
            user, "sub-" + UUID.randomUUID());
        assertEquals(409, submit.statusCode(), submit.body());
        assertEquals("CHECKIN_MORNING_PLAN_ALREADY_SET", code(submit));
        assertNull(jdbc.queryForObject("SELECT consumed_by_ref FROM momna.checkin_sessions WHERE session_id = ?",
            String.class, draft.path("sessionId").asText()));
        assertEquals(1, generationCalls(user));
    }

    /**
     * The plan's kind follows its sources, and screen A names the plan its second button will make:
     * no Check-in -> "Get General Plan", a standard plan; only Evening(D-1) -> "Get My Plan", a personal one.
     */
    @Test
    void thePlanKindFollowsItsSourcesAndScreenANamesThePlanItWillMake() throws Exception {
        var plain = user(LifecyclePeriod.CYCLE, "myday-kind-standard");
        var choice = json.readTree(get("/api/v1/my-day/today", plain.token()).body());
        assertEquals("NOT_GENERATED", choice.path("state").asText(), choice.toString());
        assertEquals("GENERAL", choice.path("planChoice").path("secondary").asText());
        assertEquals("Standard", choice.path("planChoice").path("standard").path("title").asText());
        assertEquals("Personal", choice.path("planChoice").path("personal").path("title").asText());
        assertTrue(choice.path("generationFailure").isNull());
        var standard = json.readTree(send("POST", "/api/v1/my-day/generate", "{\"source\":\"STANDARD\"}", plain,
            "gen-" + UUID.randomUUID()).body());
        assertEquals("STANDARD", standard.path("kind").asText(), standard.toString());
        assertTrue(standard.path("badge").isNull());
        var card = standard.path("cards").get(0);
        assertEquals(List.of("WHAT TO DO", "WHY TODAY", "HOW TO DO IT", "IF IT DIDN'T WORK OUT"),
            card.path("sections").findValuesAsText("header"));
        assertEquals("if skipped", card.path("sections").get(3).path("text").asText());
        assertEquals("A NOTE FROM MOMNA", card.path("note").path("badge").asText());
        assertEquals("support", card.path("note").path("body").asText());
        assertTrue(card.path("note").path("title").isNull());

        var evening = user(LifecyclePeriod.CYCLE, "myday-kind-evening");
        finalEveningYesterday(evening);
        var eveningChoice = json.readTree(get("/api/v1/my-day/today", evening.token()).body());
        assertEquals("FROM_EVENING_CHECKIN", eveningChoice.path("planChoice").path("secondary").asText(),
            eveningChoice.toString());
        assertEquals("Last Night", eveningChoice.path("planChoice").path("standard").path("title").asText());
        assertEquals("Your plan, shaped by how you felt last night.",
            eveningChoice.path("planChoice").path("standard").path("description").asText());
        var personal = json.readTree(send("POST", "/api/v1/my-day/generate", "{\"source\":\"STANDARD\"}", evening,
            "gen-" + UUID.randomUUID()).body());
        assertEquals("PERSONAL", personal.path("kind").asText(), personal.toString());
        assertEquals("Personal", personal.path("badge").asText());
        var sources = personal.path("provenance").path("sources");
        assertEquals(1, sources.size());
        assertEquals("EVENING", sources.get(0).path("phase").asText());
        assertEquals(java.time.LocalDate.parse(personal.path("localDate").asText()).minusDays(1).toString(),
            sources.get(0).path("sourceLocalDate").asText());
        assertEquals(personal.path("myDayId").asText(), jdbc.queryForObject(
            "SELECT consumed_by_ref FROM momna.checkin_sessions WHERE user_id = ? AND phase = 'EVENING'",
            String.class, evening.userId()));
    }

    /**
     * After the morning window closes with no plan, the first open makes the plan once (the one GET
     * exception, decided 23.11.2026). If generation fails, screen A comes back with the failure text.
     */
    @Test
    void afterTheMorningWindowTheFirstOpenMakesThePlanOnce() throws Exception {
        var user = user(LifecyclePeriod.CYCLE, "myday-auto", true);
        var first = json.readTree(get("/api/v1/my-day/today", user.token()).body());
        assertEquals("READY", first.path("state").asText(), first.toString());
        assertEquals("STANDARD", first.path("myDay").path("kind").asText());
        assertTrue(first.path("planChoice").isNull());
        var second = json.readTree(get("/api/v1/my-day/today", user.token()).body());
        assertEquals(first.path("myDay").path("myDayId").asText(), second.path("myDay").path("myDayId").asText());
        assertEquals(1, generationCalls(user));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM momna.my_days WHERE user_id = ?",
            Integer.class, user.userId()));

        var down = user(LifecyclePeriod.CYCLE, "myday-auto-down", true);
        SOURCE_DOWN.set(true);
        try {
            var failed = get("/api/v1/my-day/today", down.token());
            assertEquals(200, failed.statusCode(), failed.body());
            var failedJson = json.readTree(failed.body());
            assertEquals("NOT_GENERATED", failedJson.path("state").asText());
            assertEquals("We couldn't prepare your plan. Please try again.", failedJson.path("generationFailure").asText());
            assertEquals("GENERAL", failedJson.path("planChoice").path("secondary").asText());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM momna.my_days WHERE user_id = ?",
                Integer.class, down.userId()));
        } finally {
            SOURCE_DOWN.set(false);
        }
        assertEquals("READY", json.readTree(get("/api/v1/my-day/today", down.token()).body()).path("state").asText());
    }

    private com.fasterxml.jackson.databind.JsonNode morningSummary(User user) throws Exception {
        var today = get("/api/v1/check-in/today", user.token());
        assertEquals(200, today.statusCode(), today.body());
        for (var phase : json.readTree(today.body()).path("phases")) {
            if ("MORNING".equals(phase.path("phase").asText())) return phase;
        }
        throw new AssertionError("no MORNING phase: " + today.body());
    }

    private long generationCalls(User user) {
        return SOURCE_CALLS.stream().filter(call -> user.userId().equals(call.userId())).count();
    }

    /** A user whose local time is in the morning window, well clear of its edges. */
    private User user(LifecyclePeriod period, String label) throws Exception {
        return user(period, label, false);
    }

    /**
     * afternoon: local time 14:00-21:59, after the morning window (default 12:00) has closed;
     * otherwise 02:00-09:59, inside it. The zone is chosen from the real clock, so the test does not
     * depend on when CI runs.
     */
    private User user(LifecyclePeriod period, String label, boolean afternoon) throws Exception {
        String email = label + "-" + UUID.randomUUID() + "@example.test";
        DELIVERED_CODE.set(null);
        var challenge = send("POST", "/api/v1/auth/email/challenges",
            json.createObjectNode().put("email", email).toString(), null, null);
        assertEquals(202, challenge.statusCode(), challenge.body());
        var login = send("POST", "/api/v1/auth/email/complete", json.createObjectNode()
            .put("email", email)
            .put("challengeId", json.readTree(challenge.body()).path("challengeId").asText())
            .put("code", DELIVERED_CODE.get()).toString(), null, null);
        assertEquals(200, login.statusCode(), login.body());
        var credentials = json.readTree(login.body());
        String userId = authSessions.findById(credentials.path("session").path("sessionId").asText())
            .orElseThrow().getUserId();
        var profile = new UserProfileEntity(userId, Instant.now());
        String tz = zoneAt(afternoon ? 14 : 2, afternoon ? 21 : 9);
        profile.updateLocalization("en", "en-US", "US", tz, Instant.now());
        userProfiles.saveAndFlush(profile);
        lifecycleHistory.saveAndFlush(new LifecyclePeriodHistoryEntity(UUID.randomUUID().toString(), userId, period,
            null, Instant.now().minusSeconds(86400), null, label, 1.0, true));
        return new User(credentials.path("accessCredential").asText(), userId);
    }

    private static String zoneAt(int fromHour, int toHour) {
        var now = Instant.now();
        for (int offset = -12; offset <= 14; offset++) {
            int hour = now.atOffset(ZoneOffset.ofHours(offset)).getHour();
            if (hour >= fromHour && hour <= toHour) {
                return offset == 0 ? "Etc/GMT" : offset > 0 ? "Etc/GMT-" + offset : "Etc/GMT+" + (-offset);
            }
        }
        throw new IllegalStateException("no zone with local hour " + fromHour + "-" + toHour);
    }

    private int outboxCount(String eventType, String myDayId) {
        return jdbc.queryForObject("SELECT count(*) FROM momna.transactional_outbox " +
            "WHERE event_type = ? AND payload->>'myDayId' = ?", Integer.class, eventType, myDayId);
    }

    /** A final evening Check-in of the user's previous local date, with every item answered. */
    private void finalEveningYesterday(User user) {
        var profile = userProfiles.findById(user.userId()).orElseThrow();
        var zone = java.time.ZoneId.of(profile.getTimezone());
        var yesterday = java.time.LocalDate.now(zone).minusDays(1);
        var definition = checkinDefinitions.latest(
            com.momna.modules.checkin.domain.CheckinDefinitionPeriod.CYCLE,
            com.momna.modules.checkin.domain.CheckinPhase.EVENING);
        var items = definition.items().stream()
            .map(com.momna.modules.checkin.definition.WeightedCheckinDefinitionCatalog.Item::itemCode).toList();
        var opensAt = yesterday.atTime(12, 0).atZone(zone).toInstant();
        var closesAt = yesterday.atTime(23, 59, 59).atZone(zone).toInstant();
        var session = new com.momna.modules.checkin.infrastructure.CheckinSessionEntity(
            UUID.randomUUID().toString(), user.userId(), com.momna.modules.checkin.domain.CheckinPhase.EVENING,
            yesterday, "CYCLE", null, zone.getId(), definition.definitionVersion(), definition.poolVersion(),
            items, opensAt, closesAt, opensAt);
        session.updateCompletion(items.size(), opensAt.plusSeconds(60));
        session.finalizeByUser(opensAt.plusSeconds(120));
        checkinSessions.saveAndFlush(session);
    }

    /** A completed period onboarding with the given answers, as the flow engine stores them. */
    private void completedOnboarding(User user, String period, String definitionKey, java.util.Map<String, Object> answers) {
        completedOnboarding(user, period, definitionKey, 1, answers);
    }

    private void completedOnboarding(User user, String period, String definitionKey, int definitionVersion,
            java.util.Map<String, Object> answers) {
        var now = Instant.now().minusSeconds(3600);
        var instance = new com.momna.modules.flow.infrastructure.FlowInstanceEntity(UUID.randomUUID(), definitionKey,
            definitionVersion,
            com.momna.modules.flow.domain.FlowType.PERIOD_ONBOARDING, user.userId(), null, period, null, null,
            com.momna.modules.flow.domain.FlowInstanceStatus.COMPLETED, null, "start-" + UUID.randomUUID(), now, now);
        flowInstances.saveAndFlush(instance);
        for (var answer : answers.entrySet()) {
            flowAnswers.saveAndFlush(new com.momna.modules.flow.infrastructure.FlowAnswerEntity(instance.getInstanceId(),
                answer.getKey(), java.util.Map.of("value", answer.getValue()),
                com.momna.modules.flow.domain.FlowAnswerStatus.ANSWERED, "op-" + UUID.randomUUID(), now));
        }
    }

    private String code(HttpResponse<String> response) throws Exception {
        assertTrue(response.statusCode() >= 400, response.body());
        return json.readTree(response.body()).path("code").asText();
    }

    private HttpResponse<String> post(String path, User user) throws Exception {
        return send("POST", path, "{}", user, "gen-" + UUID.randomUUID());
    }

    private HttpResponse<String> postWithKey(String path, String body, User user, String key) throws Exception {
        return send("POST", path, body, user, key);
    }

    private HttpResponse<String> patch(String path, long revision, String status, User user, String key) throws Exception {
        return send("PATCH", path, json.createObjectNode().put("expectedRevision", revision)
            .put("status", status).toString(), user, key);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path, String body, User user, String key) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json");
        if (user != null) request.header("Authorization", "Bearer " + user.token());
        if (key != null) request.header("Idempotency-Key", key);
        return client.send(request.method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

}
