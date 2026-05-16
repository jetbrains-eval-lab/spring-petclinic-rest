package org.springframework.samples.petclinic.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for logging security-related events, particularly access denials.
 */
@Service
public class SecurityAuditService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditService.class);

    /**
     * Logs an access denied event for auditing purposes.
     *
     * @param principal the name of the user (or "anonymous" if unauthenticated)
     * @param resource  the resource that was attempted to be accessed
     * @param method    the HTTP method used
     */
    public void logAccessDenied(String principal, String resource, String method) {
        logger.warn("SECURITY_ACCESS_DENIED - Principal: {} attempted {} on {}", principal, method, resource);
    }
}

