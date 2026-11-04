package com.example.orders.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The value must be an IANA timezone name such as "America/New_York" (null is allowed; use @NotNull to require it). */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TimeZoneValidator.class)
public @interface ValidTimeZone {
    String message() default "must be an IANA timezone name, e.g. America/New_York";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
