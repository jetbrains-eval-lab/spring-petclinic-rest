package org.springframework.samples.petclinic.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptService.class);

    private final Map<String, UserLoginStatus> loginAttempts = new ConcurrentHashMap<>();
    private final LoginAttemptProperties properties;

    public LoginAttemptService(LoginAttemptProperties properties) {
        this.properties = properties;
    }

    /**
     * Record a failed login attempt for a given username or IP
     */
    public void registerFailedAttempt(String key) {
        if (!properties.isEnabled()) {
            return;
        }

        UserLoginStatus status = loginAttempts.getOrDefault(key, new UserLoginStatus());
        status.incrementFailedAttempts();

        if (status.getFailedAttempts() >= properties.getMaxAttempts()) {
            status.setLockedUntil(LocalDateTime.now().plusMinutes(properties.getLockDurationMinutes()));
            logger.warn("Account locked for {}: too many failed attempts ({})", key, status.getFailedAttempts());
        } else {
            logger.info("Failed login attempt {} for {}", status.getFailedAttempts(), key);
        }

        loginAttempts.put(key, status);
    }

    /**
     * Record a successful login attempt for a given username or IP
     */
    public void registerSuccessfulLogin(String key) {
        loginAttempts.remove(key);
        logger.info("Successful login for {}, resetting counter", key);
    }

    /**
     * Check if a given username or IP is currently locked out
     */
    public boolean isLocked(String key) {
        if (!properties.isEnabled()) {
            return false;
        }

        UserLoginStatus status = loginAttempts.get(key);
        if (status == null) {
            return false;
        }

        if (status.getLockedUntil() != null) {
            if (LocalDateTime.now().isAfter(status.getLockedUntil())) {
                // Lock duration has expired, reset status
                loginAttempts.remove(key);
                return false;
            }
            return true;
        }

        return false;
    }

    /**
     * Get remaining lock time in minutes
     */
    public long getRemainingLockTime(String key) {
        UserLoginStatus status = loginAttempts.get(key);
        if (status != null && status.getLockedUntil() != null) {
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(status.getLockedUntil())) {
                // Calculate remaining minutes
                return java.time.Duration.between(now, status.getLockedUntil()).toMinutes() + 1;
            }
        }
        return 0;
    }

    /**
     * Get the current failed attempt count
     */
    public int getFailedAttempts(String key) {
        UserLoginStatus status = loginAttempts.get(key);
        return status != null ? status.getFailedAttempts() : 0;
    }

    /**
     * Helper class to track user login status
     */
    private static class UserLoginStatus {
        private int failedAttempts;
        private LocalDateTime lockedUntil;

        public int getFailedAttempts() {
            return failedAttempts;
        }

        public void incrementFailedAttempts() {
            this.failedAttempts++;
        }

        public LocalDateTime getLockedUntil() {
            return lockedUntil;
        }

        public void setLockedUntil(LocalDateTime lockedUntil) {
            this.lockedUntil = lockedUntil;
        }
    }
}
