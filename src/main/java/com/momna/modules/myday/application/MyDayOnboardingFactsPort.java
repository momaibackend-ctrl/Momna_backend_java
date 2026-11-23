package com.momna.modules.myday.application;

import com.momna.modules.myday.application.template.MyDayFacts.OnboardingFact;
import java.util.Map;
import java.util.Set;

/**
 * Onboarding answers My Day may use, read through the owning module's public contract rather than its
 * tables. Only the requested allowlisted fields of the user's own latest completed onboarding for the
 * given lifecycle period are returned; a missing answer is simply absent.
 */
public interface MyDayOnboardingFactsPort {
    Map<String, OnboardingFact> facts(String userId, String lifecyclePeriod, Set<String> fieldIds);
}
