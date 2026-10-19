package com.atlas.erp.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "Informe o refresh token.")
        String refreshToken) {
}
