package com.example.orders.dto;

import com.example.orders.validation.ValidTimeZone;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /users. timezone defaults to UTC and enrolled to false when left out. */
public record CreateUserRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 255) String company,
        @Size(max = 500) String address,
        @ValidTimeZone String timezone,
        Boolean enrolled
) {
    /** Trim before validation runs, so " ada@example.com " passes @Email. */
    public CreateUserRequest {
        name = name == null ? null : name.trim();
        email = email == null ? null : email.trim();
    }
}
