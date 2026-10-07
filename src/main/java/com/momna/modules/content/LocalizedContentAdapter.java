package com.momna.modules.content;

import com.momna.platform.localization.VersionedLocaleResolver;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LocalizedContentAdapter {
    private final VersionedLocaleResolver localeResolver;

    public LocalizedContentAdapter(VersionedLocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    public VersionedLocaleResolver.ContentLocaleResolution resolveVariant(
        String contentKey,
        String requestedLocale,
        Set<String> availableLocales
    ) {
        return localeResolver.resolveContentLocale(contentKey, requestedLocale, availableLocales);
    }
}
