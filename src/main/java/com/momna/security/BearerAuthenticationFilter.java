package com.momna.security;

import com.momna.modules.auth.application.AuthApplicationService;
import com.momna.modules.auth.application.AuthException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerAuthenticationFilter extends OncePerRequestFilter {
    private final AuthApplicationService auth;

    public BearerAuthenticationFilter(AuthApplicationService auth) {
        this.auth = auth;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain
    ) throws ServletException, IOException {
        var header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            var credential = header.substring(7).trim();
            if (!credential.isEmpty()) {
                try {
                    var actor = auth.authenticate(credential);
                    var token = new UsernamePasswordAuthenticationToken(actor, credential, List.of());
                    SecurityContextHolder.getContext().setAuthentication(token);
                } catch (AuthException ignored) {
                    SecurityContextHolder.clearContext();
                }
            }
        }
        chain.doFilter(request, response);
    }
}
