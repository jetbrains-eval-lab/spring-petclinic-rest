/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.samples.petclinic.service.ApiKeyAuditService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;

/**
 * Filter that extracts API key from X-API-Key header and attempts authentication.
 * If header is absent, the filter chain continues (allowing Basic Auth to handle).
 *
 * @author Spring PetClinic Team
 */
@Component
@Profile("spring-data-jpa")
@ConditionalOnProperty(name = "petclinic.apikey.enabled", havingValue = "true", matchIfMissing = true)
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationManager authenticationManager;
    private final ApiKeyAuditService auditService;

    @Value("${petclinic.apikey.header-name:X-API-Key}")
    private String headerName;

    @Value("${petclinic.apikey.enabled:true}")
    private boolean enabled;

    @Autowired
    public ApiKeyAuthenticationFilter(@Lazy AuthenticationManager authenticationManager,
                                      ApiKeyAuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract API key from header
        String apiKey = request.getHeader(headerName);

        // If header is absent, continue filter chain (allow Basic Auth to handle)
        if (apiKey == null || apiKey.trim().isEmpty()) {
            if (shouldLogMissingKey(request)) {
                ApiKeyAuthenticationDetails details = new ApiKeyAuthenticationDetails(request);
                auditService.logAuthenticationAttempt(
                    details.getMethod(),
                    details.getPath(),
                    details.getClientIp(),
                    details.getUserAgent(),
                    null,
                    null,
                    false,
                    "MISSING_KEY",
                    false
                );
            }
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Create authentication token
            ApiKeyAuthenticationToken authRequest = new ApiKeyAuthenticationToken(apiKey.trim());
            authRequest.setDetails(new ApiKeyAuthenticationDetails(request));

            // Attempt authentication
            Authentication authResult = authenticationManager.authenticate(authRequest);

            // Set authentication in security context
            SecurityContextHolder.getContext().setAuthentication(authResult);

        } catch (AuthenticationException e) {
            // Clear security context and return 401
            SecurityContextHolder.clearContext();
            if (e instanceof SuspiciousActivityException) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("X-Suspicious-Activity", "true");
                response.getWriter().write("Too Many Requests: Suspicious activity detected");
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Unauthorized: Invalid API key");
            }
            return;
        }

        // Continue filter chain
        filterChain.doFilter(request, response);
    }

    private boolean shouldLogMissingKey(HttpServletRequest request) {
        if (auditService == null) {
            return false;
        }
        String path = request.getRequestURI();
        // URI includes context path (e.g. /petclinic/api/owners), so check for /api/ anywhere
        if (path == null || !path.contains("/api/")) {
            return false;
        }
        String authorization = request.getHeader("Authorization");
        return authorization == null || authorization.isBlank();
    }
}
