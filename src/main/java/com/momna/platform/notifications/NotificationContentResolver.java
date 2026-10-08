package com.momna.platform.notifications;

import java.time.Instant;

public interface NotificationContentResolver {
    Evidence resolve(Request request);

    record Request(
        String contentKey,
        Integer pinnedVersion,
        String locale,
        String countryRegion,
        String timezone,
        Instant effectiveAt,
        String purpose,
        String userId,
        String traceId
    ) {}

    record Evidence(
        String contentId,
        String contentKey,
        int contentVersion,
        int schemaVersion,
        String resolvedLocale,
        String localePolicyVersion,
        String countryPolicyVersion
    ) {}
}
