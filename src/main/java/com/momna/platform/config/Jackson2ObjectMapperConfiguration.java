package com.momna.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Internal Momna components still use Jackson 2 APIs. Spring Boot 4 configures
 * Jackson 3 for HTTP serialization, so provide an explicit Jackson 2 mapper for
 * those internal services instead of relying on Boot 3 auto-configuration.
 */
@Configuration(proxyBeanMethods = false)
public class Jackson2ObjectMapperConfiguration {
    @Bean
    @Primary
    public ObjectMapper jackson2ObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
