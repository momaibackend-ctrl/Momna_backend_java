package com.momna.modules.checkin.scoring;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momna.modules.checkin.definition.WeightedCheckinDefinitionCatalog;
import com.momna.modules.checkin.domain.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class MenarcheScoringCombinationTest {
    private final WeightedCheckinDefinitionCatalog definitions =
        new WeightedCheckinDefinitionCatalog(new ObjectMapper());
    private final WeightedCheckinScoringEngine scoring =
        new WeightedCheckinScoringEngine(definitions);

    @Test void hundredsOfMenarcheResponseVariantsPreserveSafetyThresholdAndNumericInvariants() {
        for (var phase : CheckinPhase.values()) {
            var definition=definitions.latest(CheckinDefinitionPeriod.MENARCHE,phase);
            var applicable=new LinkedHashSet<String>();
            for (var item : definition.items()) applicable.add(item.itemCode());
            var empty=scoring.score(CheckinDefinitionPeriod.MENARCHE,phase,
                definition.definitionVersion(),applicable,Map.of(),Map.of());
            assertEquals(0,empty.safetyScore().signum());
            assertEquals(0,empty.adjustmentScore().signum());
            assertFalse(empty.safetyThresholdReferenceReached());
            var random=new Random(0xC11E2026L+phase.ordinal());
            for (int trial=0;trial<300;trial++) {
                var answers=new LinkedHashMap<String,Integer>();
                for (var item:definition.items()) {
                    int x=random.nextInt(4);
                    if(x<3)answers.put(item.itemCode(),x);
                }
                var result=scoring.score(CheckinDefinitionPeriod.MENARCHE,phase,
                    definition.definitionVersion(),applicable,answers,Map.of());
                assertTrue(result.safetyScore().signum()>=0);
                assertTrue(result.adjustmentScore().signum()>=0);
                assertEquals(result.safetyScore().compareTo(new BigDecimal("12"))>=0,
                    result.safetyThresholdReferenceReached());
                assertEquals(answers.size(),result.answeredItemCount());
                assertTrue(result.contributions().size()<=answers.size());
                assertTrue(result.contributions().stream().allMatch(x ->
                    answers.containsKey(x.itemCode())));
            }
            var maximum=new LinkedHashMap<String,Integer>();
            for(var item:definition.items())maximum.put(item.itemCode(),2);
            var result=scoring.score(CheckinDefinitionPeriod.MENARCHE,phase,
                definition.definitionVersion(),applicable,maximum,Map.of());
            System.out.println("MENARCHE_MAX_SAFETY_"+phase+"="+result.safetyScore());
        }
    }
}
