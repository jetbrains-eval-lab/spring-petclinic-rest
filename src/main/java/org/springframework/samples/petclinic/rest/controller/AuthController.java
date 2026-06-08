package org.springframework.samples.petclinic.rest.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.samples.petclinic.service.SessionManagementService;
import org.springframework.samples.petclinic.model.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * Thin controller for OAuth2 authentication endpoints.
 * Delegates business logic to services for better maintainability.
 */
@RestController
@CrossOrigin(exposedHeaders = "errors, content-type")
@RequestMapping("/api/auth")
public class AuthController {

    private final SessionManagementService sessionService;
    private final String defaultLoginProvider;

    @Autowired
    public AuthController(
            SessionManagementService sessionService,
            @Value("${petclinic.security.oauth2.default-login-provider:google}") String defaultLoginProvider) {
        this.sessionService = sessionService;
        this.defaultLoginProvider = defaultLoginProvider;
    }

    /**
     * Initiate OAuth2 login
     * GET /api/auth/login
     */
    @GetMapping("/login")
    public ResponseEntity<Map<String, Object>> login(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        
        // Check if user is already authenticated in session
        User user = sessionService.getAuthenticatedUser(session);
        if (user != null) {
            return ResponseEntity.ok(UserResponseMapper.createAlreadyAuthenticatedResponse(user));
        }
        
        // Check Spring Security context for OAuth2 authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
            !(authentication.getPrincipal() instanceof String)) {
            
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            String provider = defaultLoginProvider;
            if (authentication instanceof OAuth2AuthenticationToken token) {
                provider = token.getAuthorizedClientRegistrationId();
            }
            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", true);
            response.put("user", UserResponseMapper.toMap(oauth2User, provider));
            return ResponseEntity.ok(response);
        }
        
        // User not authenticated - return login URL
        return ResponseEntity.ok(UserResponseMapper.createLoginRequiredResponse(defaultLoginProvider));
    }

}
