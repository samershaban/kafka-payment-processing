package com.example.orders.dto;

import com.example.orders.validation.ValidTimeZone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of PUT /users/{id}: a full replacement, so timezone and enrolled are required. */
public record UpdateUserRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 255) String company,
        @Size(max = 500) String address,
        @NotNull @ValidTimeZone String timezone,
        @NotNull Boolean enrolled
) {
    /** Trim before validation runs, so " ada@example.com " passes @Email. */
    public UpdateUserRequest {
        name = name == null ? null : name.trim();
        email = email == null ? null : email.trim();
    }
}
