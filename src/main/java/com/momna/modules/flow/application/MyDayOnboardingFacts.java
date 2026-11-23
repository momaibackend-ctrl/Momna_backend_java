package com.momna.modules.flow.application;

import com.momna.modules.flow.domain.FlowAnswerStatus;
import com.momna.modules.flow.domain.FlowInstanceStatus;
import com.momna.modules.flow.domain.FlowType;
import com.momna.modules.flow.infrastructure.FlowAnswerRepository;
import com.momna.modules.flow.infrastructure.FlowInstanceRepository;
import com.momna.modules.myday.application.MyDayOnboardingFactsPort;
import com.momna.modules.myday.application.template.MyDayFacts.OnboardingFact;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The flow module's read-only projection of onboarding answers for My Day: the active answers of the
 * user's own latest completed period onboarding, limited to the fields My Day asks for.
 */
@Component
public class MyDayOnboardingFacts implements MyDayOnboardingFactsPort {
    private final FlowInstanceRepository instances;
    private final FlowAnswerRepository answers;

    public MyDayOnboardingFacts(FlowInstanceRepository instances, FlowAnswerRepository answers) {
        this.instances = instances;
        this.answers = answers;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, OnboardingFact> facts(String userId, String lifecyclePeriod, Set<String> fieldIds) {
        var out = new TreeMap<String, OnboardingFact>();
        if (userId == null || lifecyclePeriod == null || fieldIds == null || fieldIds.isEmpty()) return out;
        var instance = instances.findFirstByUserIdAndFlowTypeAndPeriodAndStatusOrderByUpdatedAtDesc(
            userId, FlowType.PERIOD_ONBOARDING, lifecyclePeriod, FlowInstanceStatus.COMPLETED).orElse(null);
        if (instance == null || !userId.equals(instance.getUserId())) return out;
        for (var answer : answers.findByInstanceIdAndActiveTrueOrderByAnsweredAtAscAnswerIdAsc(instance.getInstanceId())) {
            if (answer.getAnswerStatus() != FlowAnswerStatus.ANSWERED || answer.getPayload() == null) continue;
            if (!fieldIds.contains(answer.getFieldId())) continue;
            out.put(answer.getFieldId(), new OnboardingFact(answer.getPayload(), answer.getAnsweredAt()));
        }
        return out;
    }
}
