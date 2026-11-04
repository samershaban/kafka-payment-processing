package com.example.orders.dto;

import com.example.orders.model.User;

import java.util.UUID;

/** What the API returns for a user. The entity itself is never serialized directly. */
public record UserResponse(
        UUID id,
        String name,
        String email,
        String company,
        String address,
        String timezone,
        boolean enrolled
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCompany(),
                user.getAddress(), user.getTimezone(), user.isEnrolled());
    }
}
