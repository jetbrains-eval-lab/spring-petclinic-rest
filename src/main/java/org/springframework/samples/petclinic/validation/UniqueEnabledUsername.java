package org.springframework.samples.petclinic.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Custom validation annotation to ensure a username is unique among enabled users
 */
@Documented
@Constraint(validatedBy = UniqueEnabledUsernameValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UniqueEnabledUsername {

    String message() default "Username already exists for an enabled user";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
