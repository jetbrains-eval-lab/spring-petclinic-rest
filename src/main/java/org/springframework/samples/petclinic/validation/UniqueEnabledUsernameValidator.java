package org.springframework.samples.petclinic.validation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.samples.petclinic.model.User;
import org.springframework.samples.petclinic.repository.UserRepository;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator implementation for UniqueEnabledUsername annotation
 */
@Component
public class UniqueEnabledUsernameValidator implements ConstraintValidator<UniqueEnabledUsername, String> {

    private final UserRepository userRepository;

    @Autowired
    public UniqueEnabledUsernameValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void initialize(UniqueEnabledUsername constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(String username, ConstraintValidatorContext context) {
        // Skip validation for null or empty values (let @NotNull or @NotEmpty handle those)
        if (username == null || username.isEmpty()) {
            return true;
        }

        // Check if a user with this username and enabled=true exists
        User existingUser = userRepository.findEnabledUserByUsername(username);
        return existingUser == null;
    }
}
