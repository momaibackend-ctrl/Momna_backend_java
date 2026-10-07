package com.momna.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "momna")
public record MomnaProperties(
    String environment,
    String role,
    String defaultLocale,
    String defaultTimezone
) {}
