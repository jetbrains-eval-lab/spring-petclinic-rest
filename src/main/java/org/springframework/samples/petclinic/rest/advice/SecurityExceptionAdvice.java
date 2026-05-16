/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.samples.petclinic.rest.advice;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.security.SecurityAuditService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URI;
import java.time.Instant;

/**
 * Global Exception handler for security-related exceptions in REST controllers.
 * <p>
 * Handles {@link AuthorizationDeniedException} and {@link AccessDeniedException}
 * thrown by @PreAuthorize checks, logs access denials, and returns 403 Forbidden.
 * <p>
 * Architecture note (current configuration):
 * - 401 Unauthorized: handled by CustomAuthenticationEntryPoint (filter chain level)
 * - 403 Forbidden: handled here (method security level, @PreAuthorize)
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionAdvice {

    private final SecurityAuditService securityAuditService;

    public SecurityExceptionAdvice(SecurityAuditService securityAuditService) {
        this.securityAuditService = securityAuditService;
    }

    /**
     * Handles {@link AuthorizationDeniedException} thrown when @PreAuthorize check fails.
     * <p>
     * In the current configuration ({@code anyRequest().authenticated()}), unauthenticated requests
     * are typically intercepted earlier by {@link org.springframework.samples.petclinic.security.CustomAuthenticationEntryPoint}
     * (returns 401), so this handler usually processes only authenticated users. Returns 403 Forbidden.
     *
     * @param e The {@link AuthorizationDeniedException} to be handled
     * @param request {@link HttpServletRequest} object referring to the current request.
     * @return A {@link ResponseEntity} containing the error information and 403 Forbidden status
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleAuthorizationDeniedException(AuthorizationDeniedException e, HttpServletRequest request) {
        return handleSecurityException(request);
    }

    /**
     * Handles {@link AccessDeniedException} thrown when access is denied to a protected resource.
     * <p>
     * In the current configuration ({@code anyRequest().authenticated()}), unauthenticated requests
     * are typically intercepted earlier by {@link org.springframework.samples.petclinic.security.CustomAuthenticationEntryPoint}
     * (returns 401), so this handler usually processes only authenticated users. Returns 403 Forbidden.
     *
     * @param e The {@link AccessDeniedException} to be handled
     * @param request {@link HttpServletRequest} object referring to the current request.
     * @return A {@link ResponseEntity} containing the error information and 403 Forbidden status
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseBody
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        return handleSecurityException(request);
    }

    /**
     * Common handler for security exceptions. Returns 403 Forbidden.
     */
    private ResponseEntity<ProblemDetail> handleSecurityException(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principal = (authentication != null && authentication.getName() != null)
            ? authentication.getName()
            : "anonymous";

        securityAuditService.logAccessDenied(principal, request.getRequestURI(), request.getMethod());

        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        detail.setType(URI.create(request.getRequestURL().toString()));
        detail.setTitle("Access Denied");
        detail.setDetail("Access Denied");
        detail.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(detail);
    }
}

