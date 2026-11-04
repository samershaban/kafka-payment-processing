package com.example.orders.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.ZoneId;

public class TimeZoneValidator implements ConstraintValidator<ValidTimeZone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        // Only region IDs ("Europe/London") or "UTC": they follow daylight saving. Fixed offsets like
        // "+02:00" or "GMT+2" are valid ZoneIds but don't, so we reject them.
        if (!value.equals("UTC") && !value.contains("/")) {
            return false;
        }
        return ZoneId.getAvailableZoneIds().contains(value) || value.equals("UTC");
    }
}
