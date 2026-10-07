package com.momna.modules.content.notifications;

import com.momna.modules.content.ContentResolutionService;
import com.momna.platform.notifications.NotificationContentResolver;
import org.springframework.stereotype.Component;

@Component
public class ContentPlatformNotificationResolver implements NotificationContentResolver {
    private final ContentResolutionService content;

    public ContentPlatformNotificationResolver(ContentResolutionService content) {
        this.content = content;
    }

    @Override
    public Evidence resolve(Request request) {
        var resolved = content.resolve(
            request.contentKey(),
            request.locale(),
            request.countryRegion(),
            request.effectiveAt(),
            "notification",
            request.purpose(),
            request.traceId(),
            request.userId(),
            request.pinnedVersion()
        );
        return new Evidence(
            resolved.contentId(),
            resolved.contentKey(),
            resolved.version(),
            resolved.schemaVersion(),
            resolved.resolvedLocale(),
            "localization.v1",
            null
        );
    }
}
