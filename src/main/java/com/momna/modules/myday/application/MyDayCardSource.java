package com.momna.modules.myday.application;

import com.momna.modules.myday.application.MyDayMaterializationPort.AdjustmentEvidence;
import com.momna.modules.myday.application.MyDayMaterializationPort.SourceReference;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Produces the content of a new My Day. The production source is the LLM path of the
 * generation spec, executed only through SafetyGuardedAi -> canonical AiGateway. When no
 * source is configured, generation fails closed: no day is stored and no Check-in is consumed.
 * My Day never invents card or medical text of its own.
 */
public interface MyDayCardSource {
    MyDayCards.GeneratedDay generate(Input input);

    record Input(
        String userId,
        LocalDate localDate,
        String timezone,
        String lifecyclePeriod,
        String lifeMode,
        String language,
        String orchestrationId,
        BigDecimal baselineShare,
        List<SourceReference> sources,
        List<AdjustmentEvidence> adjustments,
        /** The only thing that may reach the prompt and the template cache (MOMNA-1325). */
        com.momna.modules.myday.application.template.MyDayTemplateKey templateKey,
        /** Why each overlay key was or was not passed on; provenance only. */
        java.util.List<com.momna.modules.myday.application.template.MyDayOverlayAdapter.Activation> overlayActivations
    ) {}
}
