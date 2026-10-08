package com.momna.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        BearerAuthenticationFilter bearerAuthenticationFilter
    ) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.disable())
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                (request, response, failure) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
            ))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/health", "/health/live", "/health/ready", "/version",
                    "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                    "/api/v1/auth/**"
                ).permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(bearerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
