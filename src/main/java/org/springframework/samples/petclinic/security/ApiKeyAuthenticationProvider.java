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

import java.util.Arrays;
import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.samples.petclinic.model.ApiKey;
import org.springframework.samples.petclinic.service.ApiKeyAuditService;
import org.springframework.samples.petclinic.service.ApiKeyService;
import org.springframework.stereotype.Component;

/**
 * Authentication provider for API key authentication.
 * Validates API keys against the database and creates authenticated security context.
 *
 * @author Spring PetClinic Team
 */
@Component
@Profile("spring-data-jpa")
@ConditionalOnProperty(name = "petclinic.apikey.enabled", havingValue = "true", matchIfMissing = true)
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private final ApiKeyService apiKeyService;
    private final ApiKeyAuditService auditService;

    @Value("${petclinic.apikey.enabled:true}")
    private boolean enabled;

    @Autowired
    public ApiKeyAuthenticationProvider(ApiKeyService apiKeyService, ApiKeyAuditService auditService) {
        this.apiKeyService = apiKeyService;
        this.auditService = auditService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!enabled) {
            return null;
        }

        if (!(authentication instanceof ApiKeyAuthenticationToken)) {
            return null;
        }

        String apiKey = authentication.getCredentials() instanceof String
            ? (String) authentication.getCredentials()
            : null;

        // Extract request metadata from authentication details
        ApiKeyAuthenticationDetails details = null;
        if (authentication.getDetails() instanceof ApiKeyAuthenticationDetails) {
            details = (ApiKeyAuthenticationDetails) authentication.getDetails();
        }

        if (apiKey == null || apiKey.isEmpty()) {
            logAttempt(details, null, null, false, "MISSING_KEY", false);
            throw new BadCredentialsException("API key is required");
        }

        // Extract prefix for audit logging
        String keyPrefix = apiKey.length() >= 8 ? apiKey.substring(0, 8) : "UNKNOWN";

        try {
            // Validate API key
            var apiKeyOpt = apiKeyService.validateApiKey(apiKey);

            if (apiKeyOpt.isEmpty()) {
                // Determine failure reason
                String failureReason = "INVALID_KEY";
                boolean suspicious = auditService.recordFailureAndCheck(keyPrefix);
                logAttempt(details, null, keyPrefix, false, failureReason, suspicious);
                if (suspicious) {
                    throw new SuspiciousActivityException("Suspicious activity detected");
                }
                throw new BadCredentialsException("Invalid API key");
            }

            ApiKey apiKeyEntity = apiKeyOpt.get();

            // Update last used timestamp
            apiKeyService.updateLastUsedAt(apiKeyEntity.getId());

            // API keys get limited machine-to-machine access (following principle of least privilege)
            // They should only have basic API client access, not admin privileges
            Collection<GrantedAuthority> authorities = Arrays.asList(
                new SimpleGrantedAuthority("ROLE_API_CLIENT")
            );

            // Log successful authentication
            logAttempt(details, apiKeyEntity.getId(), keyPrefix, true, null, false);

            // Create authenticated token
            return new ApiKeyAuthenticationToken(null, authorities, apiKeyEntity.getCreatedBy());

        } catch (SuspiciousActivityException e) {
            throw e;
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            logAttempt(details, null, keyPrefix, false, "VALIDATION_ERROR", false);
            throw new BadCredentialsException("API key validation failed", e);
        }
    }

    private void logAttempt(ApiKeyAuthenticationDetails details, Integer apiKeyId, String keyPrefix,
                            boolean success, String failureReason, boolean suspicious) {
        if (auditService == null) {
            return;
        }
        String method = details != null ? details.getMethod() : null;
        String path = details != null ? details.getPath() : null;
        String clientIp = details != null ? details.getClientIp() : null;
        String userAgent = details != null ? details.getUserAgent() : null;
        auditService.logAuthenticationAttempt(
            method,
            path,
            clientIp,
            userAgent,
            apiKeyId,
            keyPrefix,
            success,
            failureReason,
            suspicious
        );
    }


    @Override
    public boolean supports(Class<?> authentication) {
        return ApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
