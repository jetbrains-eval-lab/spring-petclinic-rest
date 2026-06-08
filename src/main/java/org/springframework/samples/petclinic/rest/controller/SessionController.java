package org.springframework.samples.petclinic.rest.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.samples.petclinic.service.SessionManagementService;
import org.springframework.samples.petclinic.model.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.*;

/**
 * Thin controller for session management endpoints.
 * Delegates business logic to SessionManagementService for better maintainability.
 */
@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("/api/session")
public class SessionController {

    private final SessionManagementService sessionService;

    @Autowired
    public SessionController(SessionManagementService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * Get authenticated user
     * GET /api/session/user
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getAuthenticatedUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        
        // Priority 1: Check session for authenticated_user (as per requirements)
        User user = sessionService.getAuthenticatedUser(session);
        if (user != null) {
            // Update last activity time
            sessionService.updateLastActivityTime(session);
            
            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", true);
            response.put("user", UserResponseMapper.toMap(user));
            return ResponseEntity.ok(response);
        }
        
        // Priority 2: Check Spring Security context (OAuth2User)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
            !(authentication.getPrincipal() instanceof String)) {
            
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            String provider = "google";
            if (authentication instanceof OAuth2AuthenticationToken token) {
                provider = token.getAuthorizedClientRegistrationId();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", true);
            response.put("user", UserResponseMapper.toMap(oauth2User, provider));
            
            // Update last activity time if session exists
            if (session != null) {
                sessionService.updateLastActivityTime(session);
            }
            
            return ResponseEntity.ok(response);
        }
        
        // User not authenticated - return 200 OK with unauthenticated response
        // OAuth2 flow handles authentication via redirects, not 401 responses
        return ResponseEntity.ok(UserResponseMapper.createUnauthenticatedResponse());
    }

    /**
     * Get specific session attribute
     * GET /api/session/attributes/{key}
     */
    @GetMapping("/attributes/{key}")
    public ResponseEntity<Map<String, Object>> getAttribute(
            @PathVariable String key,
            HttpServletRequest request) {
        
        HttpSession session = request.getSession(false);
        
        if (session == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No active session"));
        }
        
        Object value = sessionService.getSessionAttribute(session, key);
        
        // Return 404 if attribute doesn't exist
        if (value == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Attribute not found", "key", key));
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("key", key);
        response.put("value", value);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Set/update session attribute
     * PUT /api/session/attributes/{key}
     */
    @PutMapping("/attributes/{key}")
    public ResponseEntity<Map<String, Object>> setAttribute(
            @PathVariable String key,
            @RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {
        
        HttpSession session = request.getSession(true); // Create session if it doesn't exist
        Object value = requestBody.get("value");
        
        try {
            sessionService.setSessionAttribute(session, key, value);
            
            Map<String, Object> response = new HashMap<>();
            response.put("key", key);
            response.put("value", value);
            response.put("message", "Attribute set successfully");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Remove session attribute
     * DELETE /api/session/attributes/{key}
     */
    @DeleteMapping("/attributes/{key}")
    public ResponseEntity<Map<String, Object>> removeAttribute(
            @PathVariable String key,
            HttpServletRequest request) {
        
        HttpSession session = request.getSession(false);
        
        if (session == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No active session"));
        }
        
        try {
            Object oldValue = sessionService.getSessionAttribute(session, key);
            sessionService.removeSessionAttribute(session, key);
            
            Map<String, Object> response = new HashMap<>();
            response.put("key", key);
            response.put("oldValue", oldValue);
            response.put("message", "Attribute removed successfully");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
