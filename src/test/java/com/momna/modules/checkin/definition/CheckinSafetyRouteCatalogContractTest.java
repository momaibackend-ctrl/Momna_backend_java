package com.momna.modules.checkin.definition;

import static org.junit.jupiter.api.Assertions.*;

import com.momna.modules.checkin.definition.CheckinSafetyCatalogException.Code;
import com.momna.modules.checkin.definition.CheckinSafetyRouteCatalog.ItemContribution;
import com.momna.platform.safety.CanonicalSafetyCatalog;
import com.momna.platform.safety.CanonicalSafetyEngine;
import com.momna.platform.safety.SafetyCatalogComposer;
import com.momna.platform.safety.SafetyDisposition;
import com.momna.platform.safety.SafetySeverity;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Ported from the working Kotlin CheckinSafetyRouteCatalogContractTest with the same vectors. */
class CheckinSafetyRouteCatalogContractTest {
    private final CheckinSafetyRouteCatalog catalog = new CheckinSafetyRouteCatalog();

    @Test
    void canonicalSourceExposesExactTwentyRoutesAndTwentyTwoBindings() {
        assertEquals(20, catalog.routes().size());
        assertEquals(22, catalog.bindings().size());
        assertEquals(Set.of(
            "MENARCHE_HEADACHE", "MENARCHE_DIZZINESS", "MENARCHE_BLEEDING", "CYCLE_BLEEDING",
            "PREGNANCY_SWELLING", "PREGNANCY_BREATHLESSNESS", "PREGNANCY_HEADACHE", "PREGNANCY_EPIGASTRIC",
            "PREGNANCY_GENERAL_DISCOMFORT", "POSTPARTUM_PAIN", "POSTPARTUM_BLEEDING", "POSTPARTUM_WOUND",
            "POSTPARTUM_BREAST_DISCOMFORT", "POSTPARTUM_PELVIC_DISCOMFORT", "POSTPARTUM_GENERAL_DISCOMFORT",
            "PERIMENOPAUSE_HOT_FLASHES", "PERIMENOPAUSE_UNUSUAL_CHANGE", "PERIMENOPAUSE_GENERAL_DISCOMFORT",
            "MENOPAUSE_HOT_FLASHES", "MENOPAUSE_UROGENITAL"
        ), catalog.routes().stream().map(CheckinSafetyRouteCatalog.RouteDefinition::code).collect(Collectors.toSet()));
    }

    @Test
    void canonicalBindingsMatchGovernedWeightedModelV3Content() {
        var expected = new LinkedHashMap<String, String>();
        expected.put("M3", "MENARCHE_HEADACHE");
        expected.put("M4", "MENARCHE_DIZZINESS");
        expected.put("M5", "MENARCHE_BLEEDING");
        expected.put("C4", "CYCLE_BLEEDING");
        expected.put("P6", "PREGNANCY_SWELLING");
        expected.put("P7", "PREGNANCY_BREATHLESSNESS");
        expected.put("P8", "PREGNANCY_HEADACHE");
        expected.put("P9", "PREGNANCY_EPIGASTRIC");
        expected.put("P16", "PREGNANCY_GENERAL_DISCOMFORT");
        expected.put("PP4", "POSTPARTUM_PAIN");
        expected.put("PP5", "POSTPARTUM_BLEEDING");
        expected.put("PP6", "POSTPARTUM_WOUND");
        expected.put("PP7", "POSTPARTUM_BREAST_DISCOMFORT");
        expected.put("PP8", "POSTPARTUM_PELVIC_DISCOMFORT");
        expected.put("PP14", "POSTPARTUM_GENERAL_DISCOMFORT");
        expected.put("PM2", "PERIMENOPAUSE_HOT_FLASHES");
        expected.put("PM3", "PERIMENOPAUSE_UNUSUAL_CHANGE");
        expected.put("PM9", "PERIMENOPAUSE_HOT_FLASHES");
        expected.put("PM12", "PERIMENOPAUSE_GENERAL_DISCOMFORT");
        expected.put("MN3", "MENOPAUSE_HOT_FLASHES");
        expected.put("MN8", "MENOPAUSE_UROGENITAL");
        expected.put("MN13", "MENOPAUSE_HOT_FLASHES");
        Map<String, String> actual = catalog.bindings().stream().collect(Collectors.toMap(
            CheckinSafetyRouteCatalog.Binding::itemCode, CheckinSafetyRouteCatalog.Binding::routeCode));
        assertEquals(expected, actual);
        assertTrue(catalog.bindings().stream().allMatch(b -> b.baseSafetyWeight().signum() > 0));
    }

    @Test
    void routesExposeExplicitGovernanceContract() {
        for (var route : catalog.routes()) {
            assertFalse(route.governanceDescription().isBlank());
            assertTrue(route.contentRef().startsWith("checkin.safety.route."));
            assertEquals(SafetyDisposition.REDIRECT, route.disposition());
            assertEquals(SafetySeverity.MEDIUM, route.severity());
            assertEquals(Set.of("OPEN_SAFETY_CLARIFICATION"), route.requiredActions());
            assertEquals(route.requiredActions(), route.allowedActions());
        }
        assertEquals("checkin.safety.route.menarche_headache.v3", catalog.route("MENARCHE_HEADACHE").contentRef());
        assertEquals(CanonicalSafetyCatalog.REVIEW, catalog.metadata().fallbackRouteCode());
    }

    @Test
    void exactCatalogVersionIsReproducibleAndUnknownVersionsFail() {
        assertEquals("checkin.safety.routes.v3", catalog.metadata().catalogVersion());
        assertEquals("checkin.safety.rules.v3", catalog.metadata().ruleVersion());
        assertEquals(64, catalog.metadata().sourceContentSha256().length());
        assertEquals("PREGNANCY_SWELLING", catalog.bindingForItem("P6").routeCode());
        var failure = assertThrows(CheckinSafetyCatalogException.class,
            () -> catalog.bindingForItem("P6", "checkin.safety.routes.v2"));
        assertEquals(Code.VERSION_NOT_FOUND, failure.code());
    }

    @Test
    void unknownItemAndRouteAreTypedErrors() {
        assertEquals(Code.UNKNOWN_ITEM,
            assertThrows(CheckinSafetyCatalogException.class, () -> catalog.bindingForItem("P5")).code());
        assertEquals(Code.UNKNOWN_ROUTE,
            assertThrows(CheckinSafetyCatalogException.class, () -> catalog.route("CHECKIN_UNKNOWN")).code());
    }

    @Test
    void highestItemContributionWinsAndTiesUseDisplayOrder() {
        var higher = catalog.selectRoute(List.of(
            new ItemContribution("P6", new BigDecimal("10.5")),
            new ItemContribution("P7", new BigDecimal("8.0"))));
        assertEquals("P6", higher.itemCode());
        assertEquals("PREGNANCY_SWELLING", higher.route().code());

        var tie = catalog.selectRoute(List.of(
            new ItemContribution("P7", new BigDecimal("8.0")),
            new ItemContribution("P6", new BigDecimal("8.0"))));
        assertEquals("P6", tie.itemCode());
    }

    @Test
    void selectionRejectsEmptyDuplicateOrNonPositiveContributions() {
        assertEquals(Code.INVALID_CONTRIBUTION, assertThrows(CheckinSafetyCatalogException.class,
            () -> catalog.selectRoute(List.of())).code());
        assertEquals(Code.INVALID_CONTRIBUTION, assertThrows(CheckinSafetyCatalogException.class,
            () -> catalog.selectRoute(List.of(new ItemContribution("P6", BigDecimal.ONE),
                new ItemContribution("P6", BigDecimal.TEN)))).code());
        assertEquals(Code.INVALID_CONTRIBUTION, assertThrows(CheckinSafetyCatalogException.class,
            () -> catalog.selectRoute(List.of(new ItemContribution("P6", BigDecimal.ZERO)))).code());
    }

    @Test
    void checkinContributionComposesInsideTheSafetyPlatform() {
        var contribution = CheckinSafetyPlatformContribution.contribution(catalog);
        assertEquals("CHECKIN", contribution.feature());
        assertEquals(20, contribution.routes().size());
        assertEquals(22, contribution.rules().size());
        var platform = SafetyCatalogComposer.compose(List.of(contribution), catalog.metadata().catalogVersion());
        assertEquals(catalog.metadata().catalogVersion(), platform.version());
        assertEquals(25, platform.routes().size());
        assertEquals(26, platform.rules().size());
    }

    @Test
    void deterministicCheckinItemReturnsADecisionWithoutAi() {
        var engine = engine();
        var decision = engine.evaluate(Set.of("P6"), true);
        assertEquals("PREGNANCY_SWELLING", decision.route());
        assertEquals(CanonicalSafetyEngine.SOURCE_DETERMINISTIC, decision.source());
        assertEquals(catalog.metadata().ruleVersion(), decision.ruleVersion());
        assertEquals(catalog.metadata().catalogVersion(), decision.catalogVersion());
        assertEquals(SafetySeverity.MEDIUM, decision.severity());
        assertTrue(decision.allowedActions().contains("OPEN_SAFETY_CLARIFICATION"));
    }

    @Test
    void noDeterministicItemKeepsTheClassifierUnavailableFallback() {
        var decision = engine().evaluate(Set.of(), true);
        assertEquals(CanonicalSafetyCatalog.REVIEW, decision.route());
        assertEquals(CanonicalSafetyEngine.SOURCE_FALLBACK, decision.source());
        assertEquals(CanonicalSafetyEngine.FALLBACK_CLASSIFIER_NOT_CONFIGURED, decision.fallbackState());
        assertEquals(catalog.metadata().catalogVersion(), decision.catalogVersion());
    }

    @Test
    void featureContractExposesNoRepositoriesSessionsOrPersistence() {
        for (var method : CheckinSafetyRouteCatalog.class.getMethods()) {
            var signature = method.toGenericString().toLowerCase();
            assertFalse(signature.contains("repository"), signature);
            assertFalse(signature.contains("session"), signature);
            assertFalse(signature.contains("postgres"), signature);
        }
    }

    @Test
    void bindingOfAZeroWeightItemIsRejected() throws Exception {
        var source = new String(WeightedCheckinDefinitionCatalog.canonicalSourceJson(), java.nio.charset.StandardCharsets.UTF_8);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(source);
        var first = (com.fasterxml.jackson.databind.node.ObjectNode) root.withArray("item_safety_routes").get(0);
        first.put("item_code", "P5");
        var failure = assertThrows(CheckinSafetyCatalogException.class,
            () -> CheckinSafetyRouteCatalog.fromJson(mapper.writeValueAsBytes(root)));
        assertTrue(Set.of(Code.ZERO_WEIGHT_BINDING, Code.UNKNOWN_ITEM).contains(failure.code()), failure.code().name());
    }

    @Test
    void missingBindingBreaksCoverage() throws Exception {
        var source = WeightedCheckinDefinitionCatalog.canonicalSourceJson();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(source);
        root.withArray("item_safety_routes").remove(0);
        var failure = assertThrows(CheckinSafetyCatalogException.class,
            () -> CheckinSafetyRouteCatalog.fromJson(mapper.writeValueAsBytes(root)));
        assertEquals(Code.SOURCE_INVALID, failure.code());
    }

    private CanonicalSafetyEngine engine() {
        return new CanonicalSafetyEngine(SafetyCatalogComposer.compose(
            List.of(CheckinSafetyPlatformContribution.contribution(catalog)), catalog.metadata().catalogVersion()));
    }
}
