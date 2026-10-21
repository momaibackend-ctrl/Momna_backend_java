package com.momna.platform.safety;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.auth.domain.AuthenticatedActor;
import com.momna.modules.checkin.application.CheckinDayContextService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class MedicalSafetyControllerHttpTest {
    private static final String USER_ID = "http-test-user";
    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    private MedicalSafetyRouteStateRepository states;
    private MedicalSafetyCatalog catalog;
    private MedicalSafetyFlowService flows;
    private MedicalSafetyRouteStateEntity r17;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var mapper = new ObjectMapper();
        states = mock(MedicalSafetyRouteStateRepository.class);
        catalog = new MedicalSafetyCatalog(mapper);
        flows = new MedicalSafetyFlowService(
            catalog,
            new CanonicalSafetyService(),
            states,
            mock(MedicalSafetyWb1ResponseRepository.class),
            mock(MedicalSafetyFactRevisionRepository.class)
        );

        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new MedicalSafetyController(
                flows, mock(CheckinDayContextService.class), catalog))
            .setControllerAdvice(new com.momna.shared.api.GlobalExceptionHandler())
            .setValidator(validator)
            .build();
    }

    @ParameterizedTest
    @CsvSource({"160,85", "120,110", "170,115"})
    void severePressureOnEitherAxisReturnsEmergencyOverHttp(int systolic, int diastolic)
        throws Exception {
        prepareR17(false);

        mvc.perform(post("/api/v1/medical-safety/er0/R17/blood-pressure")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systolic\":" + systolic
                    + ",\"diastolic\":" + diastolic + ",\"unknown\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("COMPLETE"))
            .andExpect(jsonPath("$.disposition").value("EMERGENCY"))
            .andExpect(jsonPath("$.actionCode").value("CALL_EMERGENCY"));

        verify(states).saveAndFlush(r17);
    }

    @ParameterizedTest
    @CsvSource({"140,85", "130,90"})
    void elevatedPressureOnEitherAxisReturnsUrgentOverHttp(int systolic, int diastolic)
        throws Exception {
        prepareR17(false);

        mvc.perform(post("/api/v1/medical-safety/er0/R17/blood-pressure")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systolic\":" + systolic
                    + ",\"diastolic\":" + diastolic + ",\"unknown\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.disposition").value("URGENT"))
            .andExpect(jsonPath("$.conditionStatus").value("CONDITION_UNDEFINED"));
    }

    @Test
    void severePressureCombinedWithHeadacheReturnsComboPeBpOverHttp() throws Exception {
        prepareR17(true);

        mvc.perform(post("/api/v1/medical-safety/er0/R17/blood-pressure")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systolic\":170,\"diastolic\":115,\"unknown\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.disposition").value("EMERGENCY"))
            .andExpect(jsonPath("$.conditionStatus").value("CONDITION_UNDEFINED:COMBO_PE_BP"));
    }

    @Test
    void belowThresholdPressureDoesNotDowngradeAConfirmedUrgentSymptom() throws Exception {
        prepareR17(true);

        mvc.perform(post("/api/v1/medical-safety/er0/R17/blood-pressure")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systolic\":120,\"diastolic\":80,\"unknown\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.disposition").value("URGENT"));
    }

    @Test
    void unknownPressureIsPersistedAsUnknownInsteadOfAReading() throws Exception {
        prepareR17(false);

        mvc.perform(post("/api/v1/medical-safety/er0/R17/blood-pressure")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systolic\":null,\"diastolic\":null,\"unknown\":true}"))
            .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(
            "UNKNOWN", r17.getFactAnswers().get("BP_READING"));
        verify(states).saveAndFlush(r17);
    }

    @Test
    void malformedMeasuredPressureReturnsBadRequestOverHttp() throws Exception {
        prepareR17(false);

        mvc.perform(post("/api/v1/medical-safety/er0/R17/blood-pressure")
                .principal(authentication())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systolic\":0,\"diastolic\":80,\"unknown\":false}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void missingAuthenticationReturnsUnauthorizedOverHttp() throws Exception {
        mvc.perform(post("/api/v1/medical-safety/er0/R17/blood-pressure")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systolic\":170,\"diastolic\":115,\"unknown\":false}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    private void prepareR17(boolean headache) {
        r17 = new MedicalSafetyRouteStateEntity(USER_ID, "R17", NOW);
        r17.begin(null, "ER0:POSTPARTUM_PREECLAMPSIA", NOW);
        for (var factCode : catalog.route("R17").questions()) {
            var value = factCode.equals("F_BP_MEASURED") ? "YES"
                : factCode.equals("F_SEVERE_HEADACHE") && headache ? "YES" : "NO";
            r17.answer(factCode, value, NOW);
        }
        r17.answer("F_BP_MEASURED", "YES", NOW);
        when(states.findByUserIdAndRouteId(USER_ID, "R17")).thenReturn(Optional.of(r17));
        when(states.findByUserId(USER_ID)).thenReturn(List.of(r17));
        when(states.saveAndFlush(any(MedicalSafetyRouteStateEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Authentication authentication() {
        var actor = new AuthenticatedActor(USER_ID, "session-http-test", NOW, NOW);
        return new UsernamePasswordAuthenticationToken(
            actor, "not-used", AuthorityUtils.NO_AUTHORITIES);
    }
}
