package com.momna.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.auth.application.EmailChallengeDelivery;
import com.momna.modules.auth.infrastructure.AuthSessionRepository;
import com.momna.modules.profile.infrastructure.*;
import com.momna.modules.lifecycle.infrastructure.*;
import com.momna.modules.lifecycle.domain.LifecyclePeriod;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
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
import static org.junit.jupiter.api.Assertions.*;

/**
 * Real HTTP + PostgreSQL E2E. Only the external email delivery is substituted,
 * in this test ApplicationContext; there is no test endpoint or backdoor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AuthenticatedApiHttpE2eTest.DeliveryConfig.class)
class AuthenticatedApiHttpE2eTest {
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
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    @Value("${local.server.port}")
    int port;

    @Autowired AuthSessionRepository authSessions;
    @Autowired UserProfileRepository userProfiles;
    @Autowired LifecyclePeriodHistoryRepository lifecycleHistory;

    @TestConfiguration(proxyBeanMethods = false)
    static class DeliveryConfig {
        @Bean
        EmailChallengeDelivery testEmailDelivery() {
            return (email, code) -> DELIVERED_CODE.set(code);
        }
    }

    private HttpResponse<String> post(String path, String body, String bearer) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json");
        if (bearer != null) req.header("Authorization", "Bearer " + bearer);
        return client.send(req.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (bearer != null) req.header("Authorization", "Bearer " + bearer);
        return client.send(req.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void emailSignInRefreshRotationAndRefreshReuseRevocation() throws Exception {
        String email = "e2e-" + UUID.randomUUID() + "@example.test";
        DELIVERED_CODE.set(null);

        var challenge = post("/api/v1/auth/email/challenges",
            json.createObjectNode().put("email", email).toString(), null);
        assertEquals(202, challenge.statusCode(), challenge.body());
        String challengeId = json.readTree(challenge.body()).path("challengeId").asText();
        assertFalse(challengeId.isBlank());
        String code = DELIVERED_CODE.get();
        assertNotNull(code, "Fake delivery in test-only context must receive OTP");

        var completeBody = json.createObjectNode()
            .put("email",email).put("challengeId",challengeId).put("code",code)
            .put("deviceLabel","Automated E2E").toString();
        var login = post("/api/v1/auth/email/complete", completeBody, null);
        assertEquals(200,login.statusCode(),login.body());
        JsonNode loginJson = json.readTree(login.body());
        String access = loginJson.path("accessCredential").asText();
        String refresh = loginJson.path("refreshCredential").asText();
        assertFalse(access.isBlank());
        assertFalse(refresh.isBlank());

        var current = get("/api/v1/me/session",access);
        assertEquals(200,current.statusCode(),current.body());

        var rotation = post("/api/v1/auth/refresh",
            json.createObjectNode().put("refreshCredential",refresh).toString(),null);
        assertEquals(200,rotation.statusCode(),rotation.body());
        String secondAccess = json.readTree(rotation.body()).path("accessCredential").asText();
        assertFalse(secondAccess.isBlank());
        assertNotEquals(access,secondAccess);
        assertEquals(200,get("/api/v1/me/session",secondAccess).statusCode());

        // Kotlin contract: a reused refresh token revokes the entire session family.
        var reuse = post("/api/v1/auth/refresh",
            json.createObjectNode().put("refreshCredential",refresh).toString(),null);
        assertEquals(401,reuse.statusCode(),reuse.body());
        assertEquals(401,get("/api/v1/me/session",secondAccess).statusCode(),
            "Replay must invalidate access in the same family");
    }

    @Test
    void onboardingRouterCanStartResumeAndReplayWithoutDuplicatingInstance() throws Exception {
        String email = "router-" + UUID.randomUUID() + "@example.test";
        DELIVERED_CODE.set(null);
        var challenge = post("/api/v1/auth/email/challenges",
            json.createObjectNode().put("email",email).toString(),null);
        assertEquals(202,challenge.statusCode(),challenge.body());
        var challengeId = json.readTree(challenge.body()).path("challengeId").asText();
        var code = DELIVERED_CODE.get();
        assertNotNull(code);
        var login = post("/api/v1/auth/email/complete",
            json.createObjectNode().put("email",email).put("challengeId",challengeId)
                .put("code",code).toString(),null);
        assertEquals(200,login.statusCode(),login.body());
        String access = json.readTree(login.body()).path("accessCredential").asText();
        assertFalse(access.isBlank());

        var key = "router-e2e-" + UUID.randomUUID();
        var body = json.createObjectNode().put("flowType","LIFECYCLE_ROUTER").toString();
        var request = HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + "/api/v1/flow-instances"))
            .header("Content-Type","application/json")
            .header("Authorization","Bearer " + access)
            .header("Idempotency-Key",key)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        var started = client.send(request,HttpResponse.BodyHandlers.ofString());
        assertEquals(200,started.statusCode(),started.body());
        var state = json.readTree(started.body());
        var instanceId = state.path("instanceId").asText();
        assertFalse(instanceId.isBlank());
        assertEquals("IN_PROGRESS",state.path("status").asText());

        var resumed = get("/api/v1/flow-instances/"+instanceId,access);
        assertEquals(200,resumed.statusCode(),resumed.body());
        assertEquals(instanceId,json.readTree(resumed.body()).path("instanceId").asText());

        var replay = client.send(request,HttpResponse.BodyHandlers.ofString());
        assertEquals(200,replay.statusCode(),replay.body());
        var replayed = json.readTree(replay.body());
        assertEquals(instanceId,replayed.path("instanceId").asText());
        assertTrue(replayed.path("idempotentReplay").asBoolean());
    }

    private HttpResponse<String> postWithKey(String path, String body,
                                             String bearer, String key) throws Exception {
        return client.send(HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type","application/json")
            .header("Authorization","Bearer " + bearer)
            .header("Idempotency-Key",key)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patchWithKey(String path, String body,
                                              String bearer, String key) throws Exception {
        return client.send(HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type","application/json")
            .header("Authorization","Bearer " + bearer)
            .header("Idempotency-Key",key)
            .method("PATCH",HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void checkinDraftPartialRemoveSubmitReplayAndImmutableFinalState() throws Exception {
        String email = "checkin-" + UUID.randomUUID() + "@example.test";
        DELIVERED_CODE.set(null);
        var challenge = post("/api/v1/auth/email/challenges",
            json.createObjectNode().put("email",email).toString(),null);
        assertEquals(202,challenge.statusCode(),challenge.body());
        String challengeId = json.readTree(challenge.body()).path("challengeId").asText();
        String code = DELIVERED_CODE.get();
        assertNotNull(code);
        var login = post("/api/v1/auth/email/complete", json.createObjectNode()
            .put("email",email).put("challengeId",challengeId).put("code",code)
            .toString(),null);
        assertEquals(200,login.statusCode(),login.body());
        var credential = json.readTree(login.body());
        String access = credential.path("accessCredential").asText();
        String authSessionId = credential.path("session").path("sessionId").asText();
        String userId = authSessions.findById(authSessionId).orElseThrow().getUserId();

        // Seed only prerequisite canonical profile/lifecycle records. All Check-in
        // transitions below go through actual authenticated HTTP controllers.
        Instant now = Instant.now();
        var profile = new UserProfileEntity(userId,now);
        profile.updateLocalization("en","en-US","US","UTC",now);
        userProfiles.saveAndFlush(profile);
        lifecycleHistory.saveAndFlush(new LifecyclePeriodHistoryEntity(
            UUID.randomUUID().toString(),userId,LifecyclePeriod.CYCLE,
            null,now.minusSeconds(86400),null,"test-fixture",1.0,true
        ));

        var today = get("/api/v1/check-in/today",access);
        assertEquals(200,today.statusCode(),today.body());
        assertTrue(json.readTree(today.body()).path("phases").isArray());
        String phase = LocalTime.now(ZoneOffset.UTC).isBefore(LocalTime.NOON)
            ? "MORNING" : "EVENING";
        var started = postWithKey("/api/v1/check-in/sessions",
            json.createObjectNode().put("phase",phase).toString(),access,
            "checkin-start-" + UUID.randomUUID());
        assertEquals(201,started.statusCode(),started.body());
        var initial=json.readTree(started.body());
        assertEquals("DRAFT",initial.path("status").asText());
        String sessionId=initial.path("sessionId").asText();
        long revision=initial.path("revision").asLong();
        String item=initial.path("items").get(0).path("itemCode").asText();
        assertFalse(item.isBlank());
        String endpoint="/api/v1/check-in/sessions/"+sessionId;

        var body1=json.createObjectNode().put("expectedRevision",revision);
        var changes1=body1.putArray("answerChanges");
        changes1.addObject().put("itemCode",item).put("value",1);
        var partial=patchWithKey(endpoint,body1.toString(),access,
            "checkin-answer-"+UUID.randomUUID());
        assertEquals(200,partial.statusCode(),partial.body());
        var partialJson=json.readTree(partial.body());
        assertEquals("PARTIAL",partialJson.path("status").asText());
        assertTrue(partialJson.path("revision").asLong()>revision);
        revision=partialJson.path("revision").asLong();

        var body2=json.createObjectNode().put("expectedRevision",revision);
        body2.putArray("answerChanges").addObject().put("itemCode",item).putNull("value");
        var removed=patchWithKey(endpoint,body2.toString(),access,
            "checkin-remove-"+UUID.randomUUID());
        assertEquals(200,removed.statusCode(),removed.body());
        var removedJson=json.readTree(removed.body());
        assertEquals("DRAFT",removedJson.path("status").asText());
        revision=removedJson.path("revision").asLong();

        var body3=json.createObjectNode().put("expectedRevision",revision);
        body3.putArray("answerChanges").addObject().put("itemCode",item).put("value",2);
        var second=patchWithKey(endpoint,body3.toString(),access,
            "checkin-second-"+UUID.randomUUID());
        assertEquals(200,second.statusCode(),second.body());
        revision=json.readTree(second.body()).path("revision").asLong();

        var submitBody=json.createObjectNode().put("expectedRevision",revision).toString();
        String submitKey="checkin-submit-"+UUID.randomUUID();
        var submit=postWithKey(endpoint+"/submit",submitBody,access,submitKey);
        assertEquals(200,submit.statusCode(),submit.body());
        assertEquals("SUBMITTED",json.readTree(submit.body())
            .path("session").path("status").asText());

        var replay=postWithKey(endpoint+"/submit",submitBody,access,submitKey);
        assertEquals(200,replay.statusCode(),replay.body());
        assertEquals("SUBMITTED",json.readTree(replay.body())
            .path("session").path("status").asText());

        var saved=get(endpoint,access);
        assertEquals(200,saved.statusCode(),saved.body());
        assertEquals("SUBMITTED",json.readTree(saved.body()).path("status").asText());

        var blocked=patchWithKey(endpoint,body3.toString(),access,
            "checkin-after-final-"+UUID.randomUUID());
        assertEquals(409,blocked.statusCode(),blocked.body());
    }

    @Test
    void accountProfileConsentLifecycleAndBillingHttpContracts() throws Exception {
        String email="account-"+UUID.randomUUID()+"@example.test";
        DELIVERED_CODE.set(null);
        var ch=post("/api/v1/auth/email/challenges",
            json.createObjectNode().put("email",email).toString(),null);
        assertEquals(202,ch.statusCode(),ch.body());
        var login=post("/api/v1/auth/email/complete",
            json.createObjectNode().put("email",email)
                .put("challengeId",json.readTree(ch.body()).path("challengeId").asText())
                .put("code",DELIVERED_CODE.get()).toString(),null);
        assertEquals(200,login.statusCode(),login.body());
        var credentials=json.readTree(login.body());
        String bearer=credentials.path("accessCredential").asText();
        String userId=authSessions.findById(credentials.path("session")
            .path("sessionId").asText()).orElseThrow().getUserId();

        var profile=new UserProfileEntity(userId,Instant.now());
        profile.updateLocalization("en","en-US","US","UTC",Instant.now());
        userProfiles.saveAndFlush(profile);
        var initial=get("/api/v1/me/profile",bearer);
        assertEquals(200,initial.statusCode(),initial.body());
        long originalVersion=json.readTree(initial.body()).path("version").asLong();

        var preferences=json.createObjectNode().put("measurementSystem","METRIC")
            .put("expectedVersion",originalVersion);
        preferences.putObject("unitPreferences").put("weight","kg");
        preferences.putObject("notificationPreferences").put("daily",true);
        var updated=client.send(HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:"+port+"/api/v1/me/profile/preferences"))
            .header("Content-Type","application/json")
            .header("Authorization","Bearer "+bearer)
            .method("PATCH",HttpRequest.BodyPublishers.ofString(preferences.toString()))
            .build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,updated.statusCode(),updated.body());
        long newVersion=json.readTree(updated.body()).path("version").asLong();
        assertTrue(newVersion>originalVersion);

        var local=json.createObjectNode().put("preferredLanguage","es")
            .put("locale","es-ES").put("countryRegion","ES")
            .put("timezone","Europe/Madrid").put("expectedVersion",newVersion);
        var localized=client.send(HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:"+port+"/api/v1/me/profile/localization"))
            .header("Content-Type","application/json")
            .header("Authorization","Bearer "+bearer)
            .method("PATCH",HttpRequest.BodyPublishers.ofString(local.toString()))
            .build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,localized.statusCode(),localized.body());
        assertEquals("Europe/Madrid",json.readTree(localized.body())
            .path("timezone").asText());

        // Optimistic locking must reject writes based on stale profile versions.
        var stale=client.send(HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:"+port+"/api/v1/me/profile/localization"))
            .header("Content-Type","application/json")
            .header("Authorization","Bearer "+bearer)
            .method("PATCH",HttpRequest.BodyPublishers.ofString(local.toString()))
            .build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(409,stale.statusCode(),stale.body());

        var consentBody=json.createObjectNode().put("policyVersion","1").toString();
        var granted=post("/api/v1/me/consents/privacy/grant",consentBody,bearer);
        assertEquals(200,granted.statusCode(),granted.body());
        assertEquals("GRANTED",json.readTree(granted.body()).path("state").asText());
        assertEquals(200,get("/api/v1/me/consents",bearer).statusCode());
        var withdrawn=post("/api/v1/me/consents/privacy/withdraw",consentBody,bearer);
        assertEquals(200,withdrawn.statusCode(),withdrawn.body());
        assertEquals("WITHDRAWN",json.readTree(withdrawn.body()).path("state").asText());

        lifecycleHistory.saveAndFlush(new LifecyclePeriodHistoryEntity(
            UUID.randomUUID().toString(),userId,LifecyclePeriod.CYCLE,
            null,Instant.now().minusSeconds(3600),null,"test-fixture",1.0,true));
        var lifecycle=get("/api/v1/me/lifecycle",bearer);
        assertEquals(200,lifecycle.statusCode(),lifecycle.body());
        assertEquals("CYCLE",json.readTree(lifecycle.body()).path("primary")
            .path("period").asText());
        assertEquals(200,get("/api/v1/me/lifecycle/history",bearer).statusCode());

        assertEquals(200,get("/api/v1/me/entitlements",bearer).statusCode());
        assertEquals(200,get("/api/v1/me/subscription",bearer).statusCode());
        assertEquals(200,get("/api/v1/me/account-center",bearer).statusCode());
        assertEquals(200,get("/api/v1/me/sessions",bearer).statusCode());
    }

    @Test
    void allSevenPeriodOnboardingRoutesStartResumeAndReplay() throws Exception {
        String email = "period-e2e-" + UUID.randomUUID() + "@example.test";
        DELIVERED_CODE.set(null);
        var challenge = post("/api/v1/auth/email/challenges",
            json.createObjectNode().put("email",email).toString(),null);
        assertEquals(202,challenge.statusCode(),challenge.body());
        var credentials = post("/api/v1/auth/email/complete",
            json.createObjectNode().put("email",email)
                .put("challengeId",json.readTree(challenge.body()).path("challengeId").asText())
                .put("code",DELIVERED_CODE.get()).toString(),null);
        assertEquals(200,credentials.statusCode(),credentials.body());
        String bearer = json.readTree(credentials.body()).path("accessCredential").asText();
        assertFalse(bearer.isBlank());

        for (String period : java.util.List.of(
            "MENARCHE", "CYCLE", "PLANNING", "PREGNANCY",
            "POSTPARTUM", "PERIMENOPAUSE", "MENOPAUSE"
        )) {
            String key = "period-" + period + "-" + UUID.randomUUID();
            String requestBody = json.createObjectNode()
                .put("flowType","PERIOD_ONBOARDING").put("period",period).toString();
            var request = HttpRequest.newBuilder(URI.create(
                "http://127.0.0.1:" + port + "/api/v1/flow-instances"
            )).header("Content-Type","application/json")
                .header("Authorization","Bearer " + bearer)
                .header("Idempotency-Key",key)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
            var started = client.send(request,HttpResponse.BodyHandlers.ofString());
            assertEquals(200,started.statusCode(),period + ": " + started.body());
            var state = json.readTree(started.body());
            assertEquals("IN_PROGRESS",state.path("status").asText(),period);
            assertEquals("period-onboarding-"+period.toLowerCase(java.util.Locale.ROOT),
                state.path("definitionKey").asText(),period);
            String instanceId = state.path("instanceId").asText();
            assertFalse(instanceId.isBlank(),period);
            assertTrue(state.path("currentStep").hasNonNull("fieldId"),period);
            var resumed = get("/api/v1/flow-instances/"+instanceId,bearer);
            assertEquals(200,resumed.statusCode(),period + ": " + resumed.body());
            assertEquals(instanceId,json.readTree(resumed.body()).path("instanceId").asText(),period);
            var replay = client.send(request,HttpResponse.BodyHandlers.ofString());
            assertEquals(200,replay.statusCode(),period + ": " + replay.body());
            assertEquals(instanceId,json.readTree(replay.body()).path("instanceId").asText(),period);
            assertTrue(json.readTree(replay.body()).path("idempotentReplay").asBoolean(),period);
        }
    }

    @Test
    void onboardingCycleSkipBackAnswerReplayAndStaleRevision() throws Exception {
        String email="cycle-transition-"+UUID.randomUUID()+"@example.test";
        DELIVERED_CODE.set(null);
        var challenge=post("/api/v1/auth/email/challenges",
            json.createObjectNode().put("email",email).toString(),null);
        assertEquals(202,challenge.statusCode(),challenge.body());
        var login=post("/api/v1/auth/email/complete",
            json.createObjectNode().put("email",email)
                .put("challengeId",json.readTree(challenge.body()).path("challengeId").asText())
                .put("code",DELIVERED_CODE.get()).toString(),null);
        assertEquals(200,login.statusCode(),login.body());
        String token=json.readTree(login.body()).path("accessCredential").asText();
        String startKey="cycle-start-"+UUID.randomUUID();
        var started=postWithKey("/api/v1/flow-instances",
            json.createObjectNode().put("flowType","PERIOD_ONBOARDING")
                .put("period","CYCLE").toString(),token,startKey);
        assertEquals(200,started.statusCode(),started.body());
        var initial=json.readTree(started.body());
        String id=initial.path("instanceId").asText();
        String firstStep=initial.path("currentStep").path("stepId").asText();
        String firstField=initial.path("currentStep").path("fieldId").asText();
        int version=initial.path("definitionVersion").asInt();
        long revision=initial.path("revision").asLong();
        assertTrue(initial.path("currentStep").path("skippable").asBoolean());

        var skipBody=json.createObjectNode().put("expectedDefinitionVersion",version)
            .put("expectedRevision",revision).toString();
        var skipped=postWithKey("/api/v1/flow-instances/"+id+"/steps/"+firstStep+"/skip",
            skipBody,token,"skip-"+UUID.randomUUID());
        assertEquals(200,skipped.statusCode(),skipped.body());
        var skippedJson=json.readTree(skipped.body());
        assertTrue(skippedJson.path("revision").asLong()>revision);
        revision=skippedJson.path("revision").asLong();

        var backBody=json.createObjectNode().put("expectedDefinitionVersion",version)
            .put("expectedRevision",revision).toString();
        var back=postWithKey("/api/v1/flow-instances/"+id+"/actions/BACK",
            backBody,token,"back-"+UUID.randomUUID());
        assertEquals(200,back.statusCode(),back.body());
        var returned=json.readTree(back.body());
        assertEquals(firstStep,returned.path("currentStep").path("stepId").asText());
        long beforeAnswer=returned.path("revision").asLong();

        var answerBody=json.createObjectNode().put("expectedDefinitionVersion",version)
            .put("expectedRevision",beforeAnswer).put("mode","SUBMIT");
        answerBody.putObject("value").put("kind","ENUM").put("value","track_cycle");
        String answerKey="answer-"+UUID.randomUUID();
        String path="/api/v1/flow-instances/"+id+"/answers/"+firstField;
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
            .header("Content-Type","application/json")
            .header("Authorization","Bearer "+token)
            .header("Idempotency-Key",answerKey)
            .PUT(HttpRequest.BodyPublishers.ofString(answerBody.toString())).build();
        var answered=client.send(request,HttpResponse.BodyHandlers.ofString());
        if (answered.statusCode() != 200) System.err.println("ONBOARDING_ANSWER_FAILURE HTTP="+answered.statusCode()+" body="+answered.body());
        assertEquals(200,answered.statusCode(),answered.body());
        var answeredJson=json.readTree(answered.body());
        assertTrue(answeredJson.path("revision").asLong()>beforeAnswer);
        assertFalse(answeredJson.path("idempotentReplay").asBoolean());

        var replay=client.send(request,HttpResponse.BodyHandlers.ofString());
        assertEquals(200,replay.statusCode(),replay.body());
        assertTrue(json.readTree(replay.body()).path("idempotentReplay").asBoolean());
        assertEquals(answeredJson.path("revision").asLong(),
            json.readTree(replay.body()).path("revision").asLong());

        var staleRequest=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
            .header("Content-Type","application/json")
            .header("Authorization","Bearer "+token)
            .header("Idempotency-Key","stale-"+UUID.randomUUID())
            .PUT(HttpRequest.BodyPublishers.ofString(answerBody.toString())).build();
        var stale=client.send(staleRequest,HttpResponse.BodyHandlers.ofString());
        assertEquals(409,stale.statusCode(),stale.body());
    }

    @Test
    void onboardingCycleCompletesAllRequiredScreensAndRecoversResult() throws Exception {
        String email="complete-cycle-"+UUID.randomUUID()+"@example.test";
        DELIVERED_CODE.set(null);
        var challenge=post("/api/v1/auth/email/challenges",
            json.createObjectNode().put("email",email).toString(),null);
        assertEquals(202,challenge.statusCode(),challenge.body());
        var login=post("/api/v1/auth/email/complete",
            json.createObjectNode().put("email",email)
                .put("challengeId",json.readTree(challenge.body()).path("challengeId").asText())
                .put("code",DELIVERED_CODE.get()).toString(),null);
        assertEquals(200,login.statusCode(),login.body());
        String token=json.readTree(login.body()).path("accessCredential").asText();
        var start=postWithKey("/api/v1/flow-instances",
            json.createObjectNode().put("flowType","PERIOD_ONBOARDING")
                .put("period","CYCLE").toString(),token,"start-"+UUID.randomUUID());
        assertEquals(200,start.statusCode(),start.body());
        var state=json.readTree(start.body());
        String id=state.path("instanceId").asText();
        int version=state.path("definitionVersion").asInt();
        String root="/api/v1/flow-instances/"+id;

        var premature=postWithKey(root+"/complete",
            json.createObjectNode().put("expectedDefinitionVersion",version)
                .put("expectedRevision",state.path("revision").asLong()).toString(),
            token,"premature-"+UUID.randomUUID());
        assertEquals(409,premature.statusCode(),premature.body());

        int answered=0;
        while (state.path("currentStep").isObject() && answered<100) {
            var step=state.path("currentStep");
            String field=step.path("fieldId").asText();
            var body=json.createObjectNode().put("expectedDefinitionVersion",version)
                .put("expectedRevision",state.path("revision").asLong())
                .put("mode","SUBMIT");
            body.putObject("value").put("kind","ENUM").put("value","no");
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+
                root+"/answers/"+field))
                .header("Content-Type","application/json")
                .header("Authorization","Bearer "+token)
                .header("Idempotency-Key","cycle-field-"+answered+"-"+UUID.randomUUID())
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            var updated=client.send(request,HttpResponse.BodyHandlers.ofString());
            assertEquals(200,updated.statusCode(),"field="+field+" "+updated.body());
            var next=json.readTree(updated.body());
            assertTrue(next.path("revision").asLong()>state.path("revision").asLong(),field);
            state=next;
            answered++;
        }
        assertTrue(answered>=10,"Cycle onboarding must exercise real user screens");
        assertTrue(answered<100,"Cycle flow must terminate, not loop");
        assertTrue(state.path("currentStep").isMissingNode() || state.path("currentStep").isNull(),
            "No required/optional step should remain after answering");

        var finished=postWithKey(root+"/complete",
            json.createObjectNode().put("expectedDefinitionVersion",version)
                .put("expectedRevision",state.path("revision").asLong()).toString(),
            token,"complete-"+UUID.randomUUID());
        assertEquals(200,finished.statusCode(),finished.body());
        assertTrue(json.readTree(finished.body()).path("completed").asBoolean());
        var recovered=get(root+"/result",token);
        assertEquals(200,recovered.statusCode(),recovered.body());
        assertTrue(json.readTree(recovered.body()).path("completed").asBoolean());
        assertEquals(answered,json.readTree(recovered.body()).path("answers").size());
    }

    @Test
    void remainingSixPeriodOnboardingsCompleteOverHttp() throws Exception {
        String email="all-periods-"+UUID.randomUUID()+"@example.test";
        DELIVERED_CODE.set(null);
        var challenge=post("/api/v1/auth/email/challenges",
            json.createObjectNode().put("email",email).toString(),null);
        assertEquals(202,challenge.statusCode(),challenge.body());
        var login=post("/api/v1/auth/email/complete",
            json.createObjectNode().put("email",email)
                .put("challengeId",json.readTree(challenge.body()).path("challengeId").asText())
                .put("code",DELIVERED_CODE.get()).toString(),null);
        assertEquals(200,login.statusCode(),login.body());
        String token=json.readTree(login.body()).path("accessCredential").asText();

        for (var period : java.util.List.of(
            "MENARCHE","PLANNING","PREGNANCY","POSTPARTUM","PERIMENOPAUSE","MENOPAUSE"
        )) {
            var start=postWithKey("/api/v1/flow-instances",
                json.createObjectNode().put("flowType","PERIOD_ONBOARDING")
                    .put("period",period).toString(),token,
                "start-"+period+"-"+UUID.randomUUID());
            assertEquals(200,start.statusCode(),period+": "+start.body());
            var state=json.readTree(start.body());
            String instanceId=state.path("instanceId").asText();
            String root="/api/v1/flow-instances/"+instanceId;
            int definitionVersion=state.path("definitionVersion").asInt();
            int steps=0;
            while (state.path("currentStep").isObject() && steps<150) {
                String field=state.path("currentStep").path("fieldId").asText();
                var body=json.createObjectNode()
                    .put("expectedDefinitionVersion",definitionVersion)
                    .put("expectedRevision",state.path("revision").asLong())
                    .put("mode","SUBMIT");
                body.putObject("value").put("kind","ENUM").put("value","no");
                var request=HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:"+port+root+"/answers/"+field))
                    .header("Content-Type","application/json")
                    .header("Authorization","Bearer "+token)
                    .header("Idempotency-Key","period-answer-"+period+"-"+steps+"-"+UUID.randomUUID())
                    .PUT(HttpRequest.BodyPublishers.ofString(body.toString())).build();
                var response=client.send(request,HttpResponse.BodyHandlers.ofString());
                assertEquals(200,response.statusCode(),period+" field="+field+" "+response.body());
                var next=json.readTree(response.body());
                assertTrue(next.path("revision").asLong()>state.path("revision").asLong(),period);
                state=next;
                steps++;
            }
            assertTrue(steps>0 && steps<150,period+" flow must terminate within 150 steps");
            assertFalse(state.path("currentStep").isObject(),period+" no step remains");

            var completed=postWithKey(root+"/complete",
                json.createObjectNode().put("expectedDefinitionVersion",definitionVersion)
                    .put("expectedRevision",state.path("revision").asLong()).toString(),
                token,"complete-"+period+"-"+UUID.randomUUID());
            assertEquals(200,completed.statusCode(),period+": "+completed.body());
            assertTrue(json.readTree(completed.body()).path("completed").asBoolean(),period);
            var recovered=get(root+"/result",token);
            assertEquals(200,recovered.statusCode(),period+": "+recovered.body());
            assertTrue(json.readTree(recovered.body()).path("completed").asBoolean(),period);
            assertEquals(instanceId,json.readTree(recovered.body())
                .path("instanceId").asText(),period);
        }
    }

    /**
     * Stateful Menarche journey: sign-in -> lifecycle router -> automatic
     * MENARCHE selection -> period onboarding -> persisted result -> check-in.
     */
    @Test
    void menarcheRouterOnboardingAndCheckinEndToEnd() throws Exception {
        String email="menarche-e2e-"+UUID.randomUUID()+"@example.test";
        DELIVERED_CODE.set(null);
        var challenge=post("/api/v1/auth/email/challenges",
            json.createObjectNode().put("email",email).toString(),null);
        assertEquals(202,challenge.statusCode(),challenge.body());
        var login=post("/api/v1/auth/email/complete",json.createObjectNode()
            .put("email",email)
            .put("challengeId",json.readTree(challenge.body()).path("challengeId").asText())
            .put("code",DELIVERED_CODE.get()).toString(),null);
        assertEquals(200,login.statusCode(),login.body());
        var credential=json.readTree(login.body());
        String token=credential.path("accessCredential").asText();
        String authSessionId=credential.path("session").path("sessionId").asText();
        String userId=authSessions.findById(authSessionId).orElseThrow().getUserId();

        var start=postWithKey("/api/v1/flow-instances",
            json.createObjectNode().put("flowType","LIFECYCLE_ROUTER").toString(),
            token,"menarche-router-"+UUID.randomUUID());
        assertEquals(200,start.statusCode(),start.body());
        JsonNode state=json.readTree(start.body());
        String routerId=state.path("instanceId").asText();
        String routerPath="/api/v1/flow-instances/"+routerId;
        int routerVersion=state.path("definitionVersion").asInt();
        int n=0;
        while(state.path("currentStep").isObject() && n<40) {
            String field=state.path("currentStep").path("fieldId").asText();
            String choice=field.equals("onboarding.router.menarche_status")
                ? "not_started" : "no";
            var payload=json.createObjectNode()
                .put("expectedDefinitionVersion",routerVersion)
                .put("expectedRevision",state.path("revision").asLong())
                .put("mode","SUBMIT");
            payload.putObject("value").put("kind","ENUM").put("value",choice);
            var request=HttpRequest.newBuilder(URI.create(
                "http://127.0.0.1:"+port+routerPath+"/answers/"+field))
                .header("Content-Type","application/json")
                .header("Authorization","Bearer "+token)
                .header("Idempotency-Key","router-field-"+n+"-"+UUID.randomUUID())
                .PUT(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            assertEquals(200,response.statusCode(),field+": "+response.body());
            state=json.readTree(response.body());n++;
        }
        assertTrue(n>=4 && n<40,"Router must reach a terminal step");
        assertFalse(state.path("currentStep").isObject());
        var completed=postWithKey(routerPath+"/complete",
            json.createObjectNode()
                .put("expectedDefinitionVersion",routerVersion)
                .put("expectedRevision",state.path("revision").asLong()).toString(),
            token,"finish-router-"+UUID.randomUUID());
        assertEquals(200,completed.statusCode(),completed.body());
        String confirmKey="confirm-menarche-"+UUID.randomUUID();
        var confirmed=postWithKey(routerPath+"/router/confirm","{}",token,confirmKey);
        assertEquals(200,confirmed.statusCode(),confirmed.body());
        var confirmation=json.readTree(confirmed.body());
        assertEquals("MENARCHE",confirmation.path("selectedPeriod").asText(),
            confirmed.body());
        assertFalse(confirmation.path("selectedManually").asBoolean());
        JsonNode flow=confirmation.path("periodFlow");
        String onboardingId=flow.path("instanceId").asText();
        assertFalse(onboardingId.isBlank());
        String onboardingPath="/api/v1/flow-instances/"+onboardingId;
        int definitionVersion=flow.path("definitionVersion").asInt();

        var early=postWithKey(onboardingPath+"/complete",json.createObjectNode()
            .put("expectedDefinitionVersion",definitionVersion)
            .put("expectedRevision",flow.path("revision").asLong()).toString(),
            token,"premature-menarche-"+UUID.randomUUID());
        assertEquals(409,early.statusCode(),early.body());

        int answered=0;
        while(flow.path("currentStep").isObject() && answered<100) {
            String field=flow.path("currentStep").path("fieldId").asText();
            var payload=json.createObjectNode()
                .put("expectedDefinitionVersion",definitionVersion)
                .put("expectedRevision",flow.path("revision").asLong())
                .put("mode","SUBMIT");
            payload.putObject("value").put("kind","ENUM").put("value","no");
            var request=HttpRequest.newBuilder(URI.create(
                "http://127.0.0.1:"+port+onboardingPath+"/answers/"+field))
                .header("Content-Type","application/json")
                .header("Authorization","Bearer "+token)
                .header("Idempotency-Key","menarche-answer-"+answered+"-"+UUID.randomUUID())
                .PUT(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            assertEquals(200,response.statusCode(),field+": "+response.body());
            flow=json.readTree(response.body());answered++;
        }
        assertTrue(answered>=12 && answered<100,
            "Menarche onboarding must traverse its actual active screens");
        assertFalse(flow.path("currentStep").isObject());
        var finishOnboarding=postWithKey(onboardingPath+"/complete",
            json.createObjectNode()
                .put("expectedDefinitionVersion",definitionVersion)
                .put("expectedRevision",flow.path("revision").asLong()).toString(),
            token,"finish-menarche-"+UUID.randomUUID());
        assertEquals(200,finishOnboarding.statusCode(),finishOnboarding.body());
        var persisted=get(onboardingPath+"/result",token);
        assertEquals(200,persisted.statusCode(),persisted.body());
        assertTrue(json.readTree(persisted.body()).path("completed").asBoolean());
        assertEquals(answered,json.readTree(persisted.body()).path("answers").size());

        var lifecycle=get("/api/v1/me/lifecycle",token);
        assertEquals(200,lifecycle.statusCode(),lifecycle.body());
        assertTrue(lifecycle.body().contains("MENARCHE"),lifecycle.body());
        // Check-in must use the period confirmed by the router, not CYCLE.
        var profile=new UserProfileEntity(userId,Instant.now());
        profile.updateLocalization("en","en-US","US","UTC",Instant.now());
        userProfiles.saveAndFlush(profile);
        var today=get("/api/v1/check-in/today",token);
        assertEquals(200,today.statusCode(),today.body());
        assertEquals("MENARCHE",json.readTree(today.body())
            .path("lifecyclePeriod").asText());
        String phase=LocalTime.now(ZoneOffset.UTC).isBefore(LocalTime.NOON)
            ? "MORNING" : "EVENING";
        var begin=postWithKey("/api/v1/check-in/sessions",
            json.createObjectNode().put("phase",phase).toString(),token,
            "menarche-checkin-"+UUID.randomUUID());
        assertEquals(201,begin.statusCode(),begin.body());
        var session=json.readTree(begin.body());
        assertEquals("DRAFT",session.path("status").asText());
        assertEquals("MENARCHE",session.path("lifecyclePeriodAtTime").asText());
        String sessionPath="/api/v1/check-in/sessions/"+
            session.path("sessionId").asText();
        String item=session.path("items").get(0).path("itemCode").asText();
        var change=json.createObjectNode()
            .put("expectedRevision",session.path("revision").asLong());
        change.putArray("answerChanges").addObject()
            .put("itemCode",item).put("value",1);
        var partial=patchWithKey(sessionPath,change.toString(),token,
            "menarche-partial-"+UUID.randomUUID());
        assertEquals(200,partial.statusCode(),partial.body());
        var partialState=json.readTree(partial.body());
        assertEquals("PARTIAL",partialState.path("status").asText());
        var stale=patchWithKey(sessionPath,change.toString(),token,
            "menarche-stale-"+UUID.randomUUID());
        assertEquals(409,stale.statusCode(),stale.body());
        var finishCheckin=postWithKey(sessionPath+"/submit",
            json.createObjectNode()
                .put("expectedRevision",partialState.path("revision").asLong())
                .toString(),token,"menarche-submit-"+UUID.randomUUID());
        assertEquals(200,finishCheckin.statusCode(),finishCheckin.body());
        assertEquals("SUBMITTED",json.readTree(finishCheckin.body())
            .path("session").path("status").asText());
        var saved=get(sessionPath,token);
        assertEquals(200,saved.statusCode(),saved.body());
        assertEquals("SUBMITTED",json.readTree(saved.body()).path("status").asText());
    }

    @Test
    void menarcheOnboardingConditionalBranchesFromRouter() throws Exception {
        for (String onset : java.util.List.of("very_recent", "within_two_years")) {
            String email="menarche-variant-"+onset+"-"+UUID.randomUUID()+"@example.test";
            DELIVERED_CODE.set(null);
            var challenge=post("/api/v1/auth/email/challenges",
                json.createObjectNode().put("email",email).toString(),null);
            assertEquals(202,challenge.statusCode(),challenge.body());
            var login=post("/api/v1/auth/email/complete",
                json.createObjectNode().put("email",email)
                    .put("challengeId",json.readTree(challenge.body()).path("challengeId").asText())
                    .put("code",DELIVERED_CODE.get()).toString(),null);
            assertEquals(200,login.statusCode(),login.body());
            String token=json.readTree(login.body()).path("accessCredential").asText();
            var started=postWithKey("/api/v1/flow-instances",
                json.createObjectNode().put("flowType","LIFECYCLE_ROUTER").toString(),
                token,"onset-router-"+UUID.randomUUID());
            assertEquals(200,started.statusCode(),started.body());
            var router=json.readTree(started.body());
            String root="/api/v1/flow-instances/"+router.path("instanceId").asText();
            int v=router.path("definitionVersion").asInt();
            for (int count=0;router.path("currentStep").isObject() && count<40;count++) {
                String field=router.path("currentStep").path("fieldId").asText();
                var data=json.createObjectNode().put("mode","SUBMIT")
                    .put("expectedDefinitionVersion",v)
                    .put("expectedRevision",router.path("revision").asLong());
                data.putObject("value").put("kind","ENUM").put("value",
                    field.equals("onboarding.router.menarche_status") ? onset : "no");
                var request=HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:"+port+root+"/answers/"+field))
                    .header("Content-Type","application/json")
                    .header("Authorization","Bearer "+token)
                    .header("Idempotency-Key","router-"+count+"-"+UUID.randomUUID())
                    .PUT(HttpRequest.BodyPublishers.ofString(data.toString())).build();
                var response=client.send(request,HttpResponse.BodyHandlers.ofString());
                assertEquals(200,response.statusCode(),field+" "+response.body());
                router=json.readTree(response.body());
            }
            assertFalse(router.path("currentStep").isObject(),onset);
            var completed=postWithKey(root+"/complete",json.createObjectNode()
                .put("expectedDefinitionVersion",v)
                .put("expectedRevision",router.path("revision").asLong()).toString(),
                token,"router-complete-"+UUID.randomUUID());
            assertEquals(200,completed.statusCode(),completed.body());
            var confirmation=postWithKey(root+"/router/confirm","{}",token,
                "router-confirm-"+UUID.randomUUID());
            assertEquals(200,confirmation.statusCode(),confirmation.body());
            var result=json.readTree(confirmation.body());
            assertEquals("MENARCHE",result.path("selectedPeriod").asText(),onset);
            var period=result.path("periodFlow");
            String periodRoot="/api/v1/flow-instances/"+period.path("instanceId").asText();
            int version=period.path("definitionVersion").asInt();
            var visited=new java.util.LinkedHashSet<String>();
            int count=0;
            while(period.path("currentStep").isObject() && count<60) {
                String field=period.path("currentStep").path("fieldId").asText();
                visited.add(field);
                var data=json.createObjectNode().put("mode","SUBMIT")
                    .put("expectedDefinitionVersion",version)
                    .put("expectedRevision",period.path("revision").asLong());
                data.putObject("value").put("kind","ENUM").put("value","no");
                var request=HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:"+port+periodRoot+"/answers/"+field))
                    .header("Content-Type","application/json")
                    .header("Authorization","Bearer "+token)
                    .header("Idempotency-Key","period-"+count+"-"+UUID.randomUUID())
                    .PUT(HttpRequest.BodyPublishers.ofString(data.toString())).build();
                var response=client.send(request,HttpResponse.BodyHandlers.ofString());
                assertEquals(200,response.statusCode(),field+" "+response.body());
                period=json.readTree(response.body());
                count++;
            }
            assertTrue(count>12 && count<60,onset+" count="+count);
            assertTrue(visited.contains("onboarding.menarche.first_experience"),
                "First experience must appear for "+onset+"; visited "+visited);
            assertTrue(visited.contains("onboarding.menarche.last_period"),
                "Last period must appear for "+onset+"; visited "+visited);
            assertFalse(visited.contains("onboarding.menarche.first_period_worry"),
                "First-period worry belongs to not_started, not "+onset);
            assertEquals("very_recent".equals(onset),
                visited.contains("onboarding.menarche.changes"),
                "Changes must be shown only for very_recent here");
        }
    }
}
