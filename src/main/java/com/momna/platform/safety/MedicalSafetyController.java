package com.momna.platform.safety;

import com.momna.modules.auth.application.AuthException;
import com.momna.modules.auth.domain.AuthenticatedActor;
import com.momna.modules.checkin.application.CheckinDayContextService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Server entrypoint for ER0 on My Day, Calendar and Check-in. */
@RestController
@RequestMapping("/api/v1/medical-safety")
public class MedicalSafetyController {
    private final MedicalSafetyFlowService flows;
    private final CheckinDayContextService contexts;
    private final MedicalSafetyCatalog catalog;

    public MedicalSafetyController(MedicalSafetyFlowService flows,
        CheckinDayContextService contexts, MedicalSafetyCatalog catalog) {
        this.flows = flows;
        this.contexts = contexts;
        this.catalog = catalog;
    }

    @PostMapping("/er0")
    public CanonicalSafetyService.MedicalStep er0(Authentication auth,
        @Valid @RequestBody Selection request) {
        var userId = actor(auth).userId();
        var period = contexts.resolve(userId, java.time.Instant.now()).period().name();
        return flows.emergencyEntry(userId, period, request.selectionCode());
    }

    @PostMapping("/er0/{routeId}/answers")
    public CanonicalSafetyService.MedicalStep answerEr0(Authentication auth,
        @PathVariable String routeId, @Valid @RequestBody Answer request) {
        return flows.answerEmergency(actor(auth).userId(), routeId, request.factCode(), request.answer());
    }

    @PostMapping("/er0/R17/blood-pressure")
    public CanonicalSafetyService.MedicalStep er0BloodPressure(Authentication auth,
        @RequestBody BloodPressureInput request) {
        return flows.emergencyBloodPressure(actor(auth).userId(), request.systolic(),
            request.diastolic(), Boolean.TRUE.equals(request.unknown()));
    }

    public record BloodPressureInput(Integer systolic, Integer diastolic, Boolean unknown) {}

    @PostMapping("/calendar/signals")
    public CanonicalSafetyService.MedicalStep calendarSignal(Authentication auth,
        @Valid @RequestBody CalendarSignal request) {
        var userId = actor(auth).userId();
        var period = contexts.resolve(userId, java.time.Instant.now()).period().name();
        return flows.calendarSignal(userId, period, request.signal());
    }

    public record CalendarSignal(@NotBlank String signal) {}

    @GetMapping("/wb1")
    public MedicalSafetyFlowService.Wb1Status wellbeingStatus(Authentication auth) {
        var userId = actor(auth).userId();
        var period = contexts.resolve(userId, java.time.Instant.now()).period().name();
        return flows.wellbeingStatus(userId, period);
    }

    @PostMapping("/wb1")
    public CanonicalSafetyService.MedicalStep wellbeing(Authentication auth,
        @Valid @RequestBody WellbeingAnswer request) {
        var userId = actor(auth).userId();
        var period = contexts.resolve(userId, java.time.Instant.now()).period().name();
        return flows.answerWellbeing(userId, period, request.answer());
    }

    @PostMapping("/wb1/answers")
    public CanonicalSafetyService.MedicalStep wellbeingFact(Authentication auth,
        @Valid @RequestBody Answer request) {
        return flows.answerWellbeingFact(actor(auth).userId(), request.factCode(), request.answer());
    }

    public record WellbeingAnswer(@NotBlank String answer) {}

    @GetMapping("/catalog")
    public CatalogView catalog(Authentication auth) {
        var userId = actor(auth).userId();
        var period = contexts.resolve(userId, java.time.Instant.now()).period().name();
        // User-facing DTO strips implementation notes and selects stage-specific text.
        return new CatalogView(MedicalSafetyCatalog.VERSION, MedicalSafetyCatalog.RULE_VERSION,
            "CLINICAL_RELEASE_NOT_APPROVED", catalog.publicRoutes(), catalog.publicFacts(period),
            catalog.questionGroups(), catalog.localizedContent());
    }

    private AuthenticatedActor actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedActor actor)) {
            throw new AuthException("AUTH_REQUIRED", "Authentication required");
        }
        return actor;
    }

    public record Selection(@NotBlank String selectionCode) {}
    public record Answer(@NotBlank String factCode, @NotBlank String answer) {}
    public record CatalogView(String catalogVersion, String ruleVersion, String releaseStatus,
        java.util.Collection<MedicalSafetyCatalog.PublicRoute> routes,
        java.util.Collection<MedicalSafetyCatalog.PublicFact> facts,
        java.util.Collection<MedicalSafetyCatalog.QuestionGroup> questionGroups,
        com.fasterxml.jackson.databind.JsonNode localizedContent) {}
}
