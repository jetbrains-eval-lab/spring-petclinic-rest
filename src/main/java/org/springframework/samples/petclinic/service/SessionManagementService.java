package org.springframework.samples.petclinic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.model.User;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for managing HTTP sessions and user authentication state.
 * Centralizes all session-related operations for better maintainability.
 */
@Service
public class SessionManagementService {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionManagementService.class);
    
    // System attributes that should not be modified via setAttribute/removeAttribute
    private static final Set<String> PROTECTED_ATTRIBUTES = Set.of(
        "SPRING_SECURITY_CONTEXT",
        "SPRING_SECURITY_SAVED_REQUEST",
        "authenticated_user"
    );
    
    // Attributes that should not be exposed in getAllSessionAttributes
    // Note: authenticated_user is NOT hidden - it should be visible for testing/monitoring
    private static final Set<String> HIDDEN_ATTRIBUTES = Set.of(
        "SPRING_SECURITY_CONTEXT",
        "SPRING_SECURITY_SAVED_REQUEST"
    );
    
    /**
     * Get authenticated user from session
     * @param session HTTP session
     * @return User object if authenticated, null otherwise
     */
    public User getAuthenticatedUser(HttpSession session) {
        if (session == null) {
            logger.debug("No active session");
            return null;
        }
        
        Object user = session.getAttribute("authenticated_user");
        if (user instanceof User) {
            logger.debug("Found authenticated user in session: {}", ((User) user).getUsername());
            return (User) user;
        }
        
        logger.debug("No authenticated user in session");
        return null;
    }
    
    /**
     * Set authenticated user in session
     * Also updates session timestamps
     * @param session HTTP session
     * @param user User to store in session
     */
    public void setAuthenticatedUser(HttpSession session, User user) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        
        session.setAttribute("authenticated_user", user);
        
        // Set session created time if not already set
        if (session.getAttribute("session_created_time") == null) {
            session.setAttribute("session_created_time", LocalDateTime.now().toString());
        }
        
        updateLastActivityTime(session);
        
        logger.info("Authenticated user set in session: {}", user.getUsername());
    }
    
    /**
     * Get comprehensive session information
     * @param session HTTP session
     * @return Map containing session details
     */
    public Map<String, Object> getSessionInfo(HttpSession session) {
        Map<String, Object> info = new HashMap<>();
        
        if (session == null) {
            info.put("active", false);
            info.put("message", "No active session");
            return info;
        }
        
        info.put("active", true);
        info.put("sessionId", session.getId());
        info.put("creationTime", session.getCreationTime());
        info.put("lastAccessedTime", session.getLastAccessedTime());
        info.put("maxInactiveInterval", session.getMaxInactiveInterval());
        info.put("isNew", session.isNew());
        
        // Add custom timestamps if available
        Object createdTime = session.getAttribute("session_created_time");
        if (createdTime != null) {
            info.put("sessionCreatedTime", createdTime);
        }
        
        Object lastActivity = session.getAttribute("last_activity_time");
        if (lastActivity != null) {
            info.put("lastActivityTime", lastActivity);
        }
        
        // Add authentication status
        User user = getAuthenticatedUser(session);
        info.put("authenticated", user != null);
        if (user != null) {
            info.put("username", user.getUsername());
            info.put("email", user.getEmail());
        }
        
        // Add session attributes for compatibility with tests
        info.put("attributes", getAllSessionAttributes(session));
        
        return info;
    }
    
    /**
     * Get all session attributes (excluding sensitive system attributes)
     * @param session HTTP session
     * @return Map of session attributes
     */
    public Map<String, Object> getAllSessionAttributes(HttpSession session) {
        if (session == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> attributes = new HashMap<>();
        Enumeration<String> attributeNames = session.getAttributeNames();
        
        while (attributeNames.hasMoreElements()) {
            String attributeName = attributeNames.nextElement();
            
            // Skip only hidden attributes (authenticated_user should be visible)
            if (!isHiddenAttribute(attributeName)) {
                Object attributeValue = session.getAttribute(attributeName);
                attributes.put(attributeName, attributeValue);
            }
        }
        
        return attributes;
    }
    
    /**
     * Get specific session attribute
     * @param session HTTP session
     * @param key Attribute key
     * @return Attribute value or null if not found
     */
    public Object getSessionAttribute(HttpSession session, String key) {
        if (session == null || key == null) {
            return null;
        }
        return session.getAttribute(key);
    }
    
    /**
     * Set session attribute
     * @param session HTTP session
     * @param key Attribute key
     * @param value Attribute value
     * @throws IllegalArgumentException if trying to set system attribute
     */
    public void setSessionAttribute(HttpSession session, String key, Object value) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        if (key == null) {
            throw new IllegalArgumentException("Attribute key cannot be null");
        }
        
        // Prevent setting protected attributes
        if (isProtectedAttribute(key)) {
            throw new IllegalArgumentException("Cannot modify protected session attribute: " + key);
        }
        
        session.setAttribute(key, value);
        updateLastActivityTime(session);
        
        logger.debug("Session attribute set: {} = {}", key, value);
    }
    
    /**
     * Remove session attribute
     * @param session HTTP session
     * @param key Attribute key
     * @throws IllegalArgumentException if trying to remove system attribute
     */
    public void removeSessionAttribute(HttpSession session, String key) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        if (key == null) {
            throw new IllegalArgumentException("Attribute key cannot be null");
        }
        
        // Prevent removing protected attributes
        if (isProtectedAttribute(key)) {
            throw new IllegalArgumentException("Cannot remove protected session attribute: " + key);
        }
        
        Object oldValue = session.getAttribute(key);
        session.removeAttribute(key);
        updateLastActivityTime(session);
        
        logger.debug("Session attribute removed: {} (was: {})", key, oldValue);
    }
    
    /**
     * Invalidate session
     * @param session HTTP session to invalidate
     */
    public void invalidateSession(HttpSession session) {
        if (session != null) {
            String sessionId = session.getId();
            try {
                session.invalidate();
                logger.info("Session invalidated: {}", sessionId);
            } catch (IllegalStateException e) {
                logger.warn("Session already invalidated: {}", sessionId);
            }
        }
    }
    
    /**
     * Update last activity timestamp
     * @param session HTTP session
     */
    public void updateLastActivityTime(HttpSession session) {
        if (session != null) {
            session.setAttribute("last_activity_time", LocalDateTime.now().toString());
        }
    }
    
    /**
     * Check if attribute is hidden and should not be exposed in getAllSessionAttributes
     * @param attributeName Attribute name to check
     * @return true if hidden attribute
     */
    private boolean isHiddenAttribute(String attributeName) {
        if (attributeName == null) {
            return false;
        }
        
        return HIDDEN_ATTRIBUTES.contains(attributeName) ||
               attributeName.startsWith("SPRING_SECURITY_");
    }
    
    /**
     * Check if attribute is protected and should not be modified
     * @param attributeName Attribute name to check
     * @return true if protected attribute
     */
    private boolean isProtectedAttribute(String attributeName) {
        if (attributeName == null) {
            return false;
        }
        
        return PROTECTED_ATTRIBUTES.contains(attributeName) ||
               attributeName.startsWith("SPRING_SECURITY_");
    }
    
    /**
     * Get authenticated user or throw exception if not authenticated
     * @param session HTTP session
     * @return User object
     * @throws IllegalStateException if user not authenticated
     */
    public User getAuthenticatedUserOrThrow(HttpSession session) {
        User user = getAuthenticatedUser(session);
        if (user == null) {
            throw new IllegalStateException("User not authenticated");
        }
        return user;
    }
    
    /**
     * Check if session has authenticated user
     * @param session HTTP session
     * @return true if authenticated
     */
    public boolean isAuthenticated(HttpSession session) {
        return getAuthenticatedUser(session) != null;
    }
}