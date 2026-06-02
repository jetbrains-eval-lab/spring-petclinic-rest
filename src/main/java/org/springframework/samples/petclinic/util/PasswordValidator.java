package org.springframework.samples.petclinic.util;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Component for validating password strength.
 * Implements configurable password policy rules to ensure password security.
 */
@Component
public class PasswordValidator {

    private final Pattern passwordPattern;
    private final int minimumLength;

    public PasswordValidator(
            @Value("${password.policy.minimum-length:5}") int minimumLength,
            @Value("${password.policy.pattern:^(?=.*[0-9])(?=.*[a-zA-Z])(?=.*[^a-zA-Z0-9]).*$}") String passwordPatternRegex) {

        this.minimumLength = minimumLength;
        this.passwordPattern = Pattern.compile(passwordPatternRegex);
    }

    /**
     * Validates if a password meets the strength requirements.
     * Password must:
     * - Be longer than the configured minimum length
     * - Match the configured pattern for complexity (digits, letters, special characters)
     *
     * @param password The password to validate
     * @return true if the password meets all requirements, false otherwise
     */
    public boolean isValid(String password) {
        if (password == null || password.length() <= minimumLength) {
            return false;
        }

        return passwordPattern.matcher(password).matches();
    }

    /**
     * Returns a detailed validation message for an invalid password.
     *
     * @param password The password to validate
     * @return A message describing why the password is invalid, or null if it's valid
     */
    public String validatePassword(String password) {
        if (password == null) {
            return "Password cannot be null";
        }

        if (password.length() <= minimumLength) {
            return "Password must be longer than " + minimumLength + " characters";
        }

        if (!passwordPattern.matcher(password).matches()) {
            return "Password must contain at least one digit, one letter, and one special character";
        }

        return null; // Password is valid
    }
}
