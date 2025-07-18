package org.springframework.samples.petclinic.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class LoginAttemptFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptFilter.class);
    private final LoginAttemptService loginAttemptService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LoginAttemptFilter(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Check if this is a login request with basic auth
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            // Extract username
            String base64Credentials = authHeader.substring("Basic ".length()).trim();
            String credentials = new String(Base64.getDecoder().decode(base64Credentials));
            String username = credentials.split(":", 2)[0];

            // Check if the username is locked
            if (loginAttemptService.isLocked(username)) {
                long remainingMinutes = loginAttemptService.getRemainingLockTime(username);
                logger.warn("Blocked authentication attempt from locked account: {}", username);

                // Send locked account response
                Map<String, Object> errorDetails = new HashMap<>();
                errorDetails.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
                errorDetails.put("error", "Too Many Attempts");
                errorDetails.put("message", "Account is temporarily locked due to too many failed login attempts. " +
                        "Please try again in " + remainingMinutes + " minutes.");

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                objectMapper.writeValue(response.getWriter(), errorDetails);
                return;
            }

            // Check IP address too
            String clientIP = getClientIP(request);
            if (loginAttemptService.isLocked(clientIP)) {
                long remainingMinutes = loginAttemptService.getRemainingLockTime(clientIP);
                logger.warn("Blocked authentication attempt from locked IP: {}", clientIP);

                // Send locked IP response
                Map<String, Object> errorDetails = new HashMap<>();
                errorDetails.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
                errorDetails.put("error", "Too Many Attempts");
                errorDetails.put("message", "Too many failed login attempts from your IP address. " +
                        "Please try again in " + remainingMinutes + " minutes.");

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                objectMapper.writeValue(response.getWriter(), errorDetails);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}
