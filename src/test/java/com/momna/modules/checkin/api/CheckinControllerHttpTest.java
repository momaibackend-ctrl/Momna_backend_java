package com.momna.modules.checkin.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.auth.domain.AuthenticatedActor;
import com.momna.modules.checkin.application.CheckinApplicationService;
import com.momna.shared.api.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class CheckinControllerHttpTest {
    private static final String USER_ID = "http-test-user";
    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    private CheckinApplicationService checkin;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        checkin = mock(CheckinApplicationService.class);
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new CheckinController(checkin))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(validator)
            .build();
    }

    @Test
    void postpartumBleedingEmergencyFactIsAcceptedThroughSafetyAnswerHttpRoute() throws Exception {
        mvc.perform(post("/api/v1/check-in/sessions/session-1/safety/R12/answers")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"factCode\":\"F_BLEED_SUDDEN_INCREASE\",\"answer\":\"YES\"}"))
            .andExpect(status().isOk());

        verify(checkin).answerSafety(
            USER_ID, "session-1", "R12", "F_BLEED_SUDDEN_INCREASE", "YES");
    }

    @Test
    void postpartumPresyncopeEmergencyFactIsAcceptedThroughSafetyAnswerHttpRoute() throws Exception {
        mvc.perform(post("/api/v1/check-in/sessions/session-1/safety/R12/answers")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"factCode\":\"F_PRESYNCOPE\",\"answer\":\"YES\"}"))
            .andExpect(status().isOk());

        verify(checkin).answerSafety(USER_ID, "session-1", "R12", "F_PRESYNCOPE", "YES");
    }

    @Test
    void systemicChecklistIsSubmittedAsOneHttpRequestAndOneServiceCall() throws Exception {
        mvc.perform(post("/api/v1/check-in/sessions/session-1/safety/R16/checklist")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"SELECTED\",\"selectedFactCodes\":[\"F_HEART_RACING\"]}"))
            .andExpect(status().isOk());

        verify(checkin).answerSafetyChecklist(
            USER_ID, "session-1", "R16", "SELECTED", List.of("F_HEART_RACING"));
    }

    @Test
    void checkinBloodPressurePayloadIsBoundToTheSafetyService() throws Exception {
        mvc.perform(post("/api/v1/check-in/sessions/session-1/safety/R17/blood-pressure")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systolic\":170,\"diastolic\":115,\"unknown\":false}"))
            .andExpect(status().isOk());

        verify(checkin).recordSafetyBloodPressure(
            USER_ID, "session-1", "R17", 170, 115, false);
    }

    @Test
    void explicitUnknownBloodPressureIsNotConvertedToZeroOrNormal() throws Exception {
        mvc.perform(post("/api/v1/check-in/sessions/session-1/safety/R17/blood-pressure")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systolic\":null,\"diastolic\":null,\"unknown\":true}"))
            .andExpect(status().isOk());

        verify(checkin).recordSafetyBloodPressure(
            USER_ID, "session-1", "R17", null, null, true);
    }

    @Test
    void safetyAnswerWithBlankFactCodeFailsHttpValidation() throws Exception {
        mvc.perform(post("/api/v1/check-in/sessions/session-1/safety/R12/answers")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"factCode\":\" \",\"answer\":\"YES\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void safetyRoutesRejectRequestsWithoutAnAuthenticatedActor() throws Exception {
        mvc.perform(post("/api/v1/check-in/sessions/session-1/safety/R12/answers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"factCode\":\"F_PRESYNCOPE\",\"answer\":\"YES\"}"))
            .andExpect(status().isUnauthorized());
    }

    private Authentication authentication() {
        var actor = new AuthenticatedActor(USER_ID, "session-http-test", NOW, NOW);
        return new UsernamePasswordAuthenticationToken(
            actor, "not-used", AuthorityUtils.NO_AUTHORITIES);
    }
}
