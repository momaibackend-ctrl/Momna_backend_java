package com.momna.modules.medicalsafety.api;

import com.momna.modules.auth.application.AuthException;
import com.momna.modules.auth.domain.AuthenticatedActor;
import com.momna.modules.medicalsafety.application.MedicalSafetyApplicationService;
import com.momna.modules.medicalsafety.application.MedicalSafetyApplicationService.StepView;
import com.momna.modules.medicalsafety.application.MedicalSafetyApplicationService.Wb1View;
import com.momna.platform.safety.MedicalSafetyCatalog;
import com.momna.platform.safety.MedicalSafetyEr0Catalog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Collection;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * The global medical safety surfaces of catalog v2.2 (MOMNA-1145): ER0 on My Day, Calendar and Check-in, the
 * postpartum WB1 card, calendar signals, and the public catalog the client renders. Every write needs an
 * Idempotency-Key; the lifecycle period is always the server's.
 */
@RestController
@RequestMapping("/api/v1/medical-safety")
public class MedicalSafetyController {
    private final MedicalSafetyApplicationService safety;
    private final MedicalSafetyCatalog catalog;

    public MedicalSafetyController(MedicalSafetyApplicationService safety, MedicalSafetyCatalog catalog) {
        this.safety = safety;
        this.catalog = catalog;
    }

    /** The ER0 screen for the user's period: texts and the signs to show. */
    @GetMapping("/er0")
    public MedicalSafetyEr0Catalog.Screen er0(Authentication auth) {
        return safety.er0Screen(actor(auth).userId());
    }

    /** One tap on an ER0 sign, or selectionCode NONE for "None of these". */
    @PostMapping("/er0")
    public StepView er0(Authentication auth, @RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody Selection request) {
        return safety.emergency(actor(auth).userId(), request.selectionCode(), key);
    }

    /** The current question of a route an ER0, WB1 or calendar entry started or joined. */
    @PostMapping("/routes/{routeId}/answers")
    public StepView answer(Authentication auth, @PathVariable String routeId,
        @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Answer request) {
        return safety.answer(actor(auth).userId(), routeId, request.factCode(), request.answer(), key);
    }

    @PostMapping("/routes/{routeId}/blood-pressure")
    public StepView bloodPressure(Authentication auth, @PathVariable String routeId,
        @RequestHeader("Idempotency-Key") String key, @RequestBody BloodPressure request) {
        return safety.bloodPressure(actor(auth).userId(), routeId, request.systolic(), request.diastolic(),
            Boolean.TRUE.equals(request.unknown()), key);
    }

    /** An explicit signal of a saved calendar entry. */
    @PostMapping("/calendar/signals")
    public StepView calendarSignal(Authentication auth, @RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody CalendarSignal request) {
        return safety.calendarSignal(actor(auth).userId(), request.signal(), key);
    }

    /** Whether to show the WB1 card now, with its texts. */
    @GetMapping("/wb1")
    public Wb1View wellbeing(Authentication auth) {
        return safety.wellbeing(actor(auth).userId());
    }

    @PostMapping("/wb1")
    public StepView answerWellbeing(Authentication auth, @RequestHeader("Idempotency-Key") String key,
        @Valid @RequestBody WellbeingAnswer request) {
        return safety.answerWellbeing(actor(auth).userId(), request.answer(), key);
    }

    /** The public v2.2 catalog for online rendering: no rules, notes or mixed-stage texts. */
    @GetMapping("/catalog")
    public CatalogView catalog(Authentication auth) {
        var period = safety.lifecyclePeriod(actor(auth).userId());
        return new CatalogView(MedicalSafetyCatalog.VERSION, MedicalSafetyCatalog.RULE_VERSION,
            "CLINICAL_RELEASE_NOT_APPROVED", catalog.publicRoutes(), catalog.publicFacts(period),
            catalog.questionGroups(), catalog.publicScreens(period));
    }

    private AuthenticatedActor actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedActor actor)) {
            throw new AuthException("AUTH_REQUIRED", "Authentication required");
        }
        return actor;
    }

    public record Selection(@NotBlank String selectionCode) {}
    public record Answer(@NotBlank String factCode, @NotBlank String answer) {}
    public record BloodPressure(Integer systolic, Integer diastolic, Boolean unknown) {}
    public record CalendarSignal(@NotBlank String signal) {}
    public record WellbeingAnswer(@NotBlank String answer) {}
    public record CatalogView(String catalogVersion, String ruleVersion, String releaseStatus,
        Collection<MedicalSafetyCatalog.PublicRoute> routes, Collection<MedicalSafetyCatalog.PublicFact> facts,
        Collection<MedicalSafetyCatalog.QuestionGroup> questionGroups,
        com.fasterxml.jackson.databind.JsonNode localizedContent) {}
}
