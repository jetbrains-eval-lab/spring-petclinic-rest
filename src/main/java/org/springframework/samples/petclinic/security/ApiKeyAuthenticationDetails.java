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

import org.springframework.security.web.authentication.WebAuthenticationDetails;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Custom authentication details that stores request metadata
 * for use in authentication provider (e.g., for audit logging).
 *
 * @author Spring PetClinic Team
 */
public class ApiKeyAuthenticationDetails extends WebAuthenticationDetails {

    private final String method;
    private final String path;
    private final String clientIp;
    private final String userAgent;

    /**
     * Creates authentication details from the HTTP request.
     *
     * @param request the HTTP request
     */
    public ApiKeyAuthenticationDetails(HttpServletRequest request) {
        super(request);
        this.method = request.getMethod();
        this.path = request.getRequestURI();
        this.clientIp = extractClientIp(request);
        this.userAgent = request.getHeader("User-Agent");
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
