package com.atlas.erp.users.dto;

import com.atlas.erp.users.Role;
import com.atlas.erp.users.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        Role role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
