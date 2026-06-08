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
package org.springframework.samples.petclinic.service;

/**
 * Service interface for API key audit logging operations.
 *
 * @author Spring PetClinic Team
 */
public interface ApiKeyAuditService {

    /**
     * Log an authentication attempt to application logs.
     *
     * @param method HTTP method
     * @param path request path
     * @param clientIp client IP address
     * @param userAgent user agent
     * @param apiKeyId the API key ID if authentication succeeded, null if failed
     * @param keyPrefix the key prefix (for failed attempts)
     * @param success whether authentication succeeded
     * @param failureReason the failure reason if authentication failed
     * @param suspicious whether suspicious activity was detected
     */
    void logAuthenticationAttempt(String method, String path, String clientIp, String userAgent,
                                  Integer apiKeyId, String keyPrefix, boolean success,
                                  String failureReason, boolean suspicious);

    /**
     * Detect suspicious activity for a given key prefix.
     * Checks for multiple failures within the configured time window.
     *
     * @param keyPrefix the key prefix to check
     * @return true if suspicious activity detected (threshold exceeded)
     */
    boolean detectSuspiciousActivity(String keyPrefix);

    /**
     * Record a failed authentication attempt for the given key prefix and
     * return whether the failure threshold has been reached.
     *
     * @param keyPrefix the key prefix to record
     * @return true if suspicious activity detected (threshold reached)
     */
    boolean recordFailureAndCheck(String keyPrefix);
}
