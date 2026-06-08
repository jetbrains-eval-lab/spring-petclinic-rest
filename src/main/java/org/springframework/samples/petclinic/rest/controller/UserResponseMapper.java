package org.springframework.samples.petclinic.rest.controller;

import org.springframework.samples.petclinic.model.User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for mapping User entities and OAuth2User objects to JSON response format.
 * Eliminates code duplication across controllers.
 */
@Component
public class UserResponseMapper {
    
    /**
     * Convert User entity to JSON response map
     * @param user User entity from database/session
     * @return Map suitable for JSON response
     */
    public static Map<String, Object> toMap(User user) {
        Map<String, Object> userResponse = new HashMap<>();
        
        if (user == null) {
            return userResponse;
        }
        
        // Ensure email is always available (email → username/email mapping requirement)
        String email = user.getEmail();
        if (email == null || email.isEmpty()) {
            email = user.getUsername(); // Fallback to username which contains email
        }
        if (email == null || email.isEmpty()) {
            // Last resort - this should not happen if mapping is correct
            email = "unknown@example.com";
        }
        
        userResponse.put("email", email);
        userResponse.put("username", user.getUsername());
        userResponse.put("firstName", user.getFirstName());
        userResponse.put("lastName", user.getLastName());
        userResponse.put("picture", user.getPictureUrl());
        userResponse.put("provider", user.getOauthProvider());
        userResponse.put("enabled", user.getEnabled());
        
        // Get roles from User entity
        if (user.getRoles() != null) {
            userResponse.put("roles", user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toList()));
        } else {
            userResponse.put("roles", new java.util.ArrayList<>());
        }
        
        return userResponse;
    }
    
    /**
     * Convert OAuth2User to JSON response map
     * @param oauth2User OAuth2User from Spring Security
     * @return Map suitable for JSON response
     */
    public static Map<String, Object> toMap(OAuth2User oauth2User) {
        return toMap(oauth2User, "google");
    }

    /**
     * Convert OAuth2User to JSON response map with explicit provider id.
     * @param oauth2User OAuth2User from Spring Security
     * @param provider OAuth2 registration id (google, github, etc.)
     * @return Map suitable for JSON response
     */
    public static Map<String, Object> toMap(OAuth2User oauth2User, String provider) {
        Map<String, Object> userResponse = new HashMap<>();
        
        if (oauth2User == null) {
            return userResponse;
        }
        
        userResponse.put("email", oauth2User.getAttribute("email"));
        userResponse.put("name", oauth2User.getAttribute("name"));
        userResponse.put("givenName", oauth2User.getAttribute("given_name"));
        userResponse.put("familyName", oauth2User.getAttribute("family_name"));
        userResponse.put("picture", oauth2User.getAttribute("picture"));
        userResponse.put("provider", provider);
        
        return userResponse;
    }
    
    /**
     * Convert session user object to JSON response map
     * Handles both User entity and OAuth2User objects that might be stored in session
     * @param sessionUser Object from session (User or OAuth2User)
     * @return Map suitable for JSON response
     */
    public static Map<String, Object> fromSessionObject(Object sessionUser) {
        if (sessionUser == null) {
            return new HashMap<>();
        }
        
        if (sessionUser instanceof User) {
            return toMap((User) sessionUser);
        } else if (sessionUser instanceof OAuth2User) {
            // Fallback for OAuth2User in session
            return toMap((OAuth2User) sessionUser);
        } else {
            // Unknown session object type
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Unknown user type in session: " + sessionUser.getClass().getSimpleName());
            return response;
        }
    }
    
    /**
     * Create authenticated user response with session info
     * @param user User entity
     * @param sessionId Session ID
     * @param sessionCreatedTime Session creation time
     * @param lastActivityTime Last activity time
     * @return Complete response map
     */
    public static Map<String, Object> createAuthenticatedResponse(
            User user, 
            String sessionId, 
            Object sessionCreatedTime, 
            Object lastActivityTime) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", true);
        response.put("user", toMap(user));
        
        if (sessionId != null) {
            response.put("sessionId", sessionId);
        }
        if (sessionCreatedTime != null) {
            response.put("sessionCreatedTime", sessionCreatedTime);
        }
        if (lastActivityTime != null) {
            response.put("lastActivityTime", lastActivityTime);
        }
        
        return response;
    }
    
    /**
     * Create unauthenticated user response
     * @return Response map for unauthenticated state
     */
    public static Map<String, Object> createUnauthenticatedResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", false);
        response.put("user", null);
        return response;
    }
    
    /**
     * Create login response for already authenticated user
     * @param user User entity
     * @return Response map for login endpoint
     */
    public static Map<String, Object> createAlreadyAuthenticatedResponse(User user) {
        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", true);
        response.put("message", "User already authenticated");
        response.put("user", toMap(user));
        return response;
    }
    
    /**
     * Create login response for unauthenticated user
     * @return Response map with login URL
     */
    public static Map<String, Object> createLoginRequiredResponse() {
        return createLoginRequiredResponse("google");
    }

    /**
     * Create login response for unauthenticated user with configurable provider.
     * @param provider OAuth2 provider registration id
     * @return Response map with login URL
     */
    public static Map<String, Object> createLoginRequiredResponse(String provider) {
        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", false);
        response.put("loginUrl", "/oauth2/authorization/" + provider);
        return response;
    }
}
