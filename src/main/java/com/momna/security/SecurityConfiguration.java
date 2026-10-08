package com.momna.security;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, failure) -> {
                var requestId = request.getHeader("X-Request-Id");
                if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"code\":\"AUTH_REQUIRED\",\"message\":\"Authentication required\",\"requestId\":\"" +
                    jsonEscape(requestId) + "\"}"
                );
            }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/health", "/health/live", "/health/ready", "/version",
                    "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                    "/api/v1/auth/**"
                ).permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .addFilterBefore(bearerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
