package com.careconnect.dto;

import com.careconnect.entity.Role;
import com.careconnect.entity.User;

/** Safe view of a user: deliberately has NO password hash field. */
public record UserResponse(Long id, String name, String email, Role role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
