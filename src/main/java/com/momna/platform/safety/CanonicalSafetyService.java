package com.momna.platform.safety;

import java.util.Set;
import org.springframework.stereotype.Service;

/** Platform safety over the base catalog, for features without their own contribution. */
@Service
public class CanonicalSafetyService {
    public static final String CATALOG_VERSION = CanonicalSafetyCatalog.VERSION;
    public static final String BASELINE_RULE_VERSION = CanonicalSafetyCatalog.BASELINE_RULE_VERSION;
    public static final String CRITICAL_RULE_VERSION = CanonicalSafetyCatalog.CRITICAL_RULE_VERSION;
    public static final String CLINICAL_RULE_VERSION = CanonicalSafetyCatalog.CLINICAL_RULE_VERSION;
    public static final String RELATIONSHIP_RULE_VERSION = CanonicalSafetyCatalog.RELATIONSHIP_RULE_VERSION;

    private final CanonicalSafetyEngine engine = new CanonicalSafetyEngine(CanonicalSafetyCatalog.create());

    public SafetyDecision evaluate(Set<String> signalKeys) {
        return engine.evaluate(signalKeys, false);
    }
}
