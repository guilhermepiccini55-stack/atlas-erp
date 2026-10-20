package com.atlas.erp.users.dto;

import com.atlas.erp.users.Role;
import com.atlas.erp.users.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record UserResponse(
        @Schema(description = "ID numérico BIGINT do usuário")
        Long id,
        String name,
        String email,
        Role role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
