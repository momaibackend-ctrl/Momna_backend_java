package com.momna.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MomnaProperties.class)
public class ApplicationConfiguration {
    @Bean
    public ObjectMapper legacyJacksonObjectMapper() {
        return new ObjectMapper();
    }
}
