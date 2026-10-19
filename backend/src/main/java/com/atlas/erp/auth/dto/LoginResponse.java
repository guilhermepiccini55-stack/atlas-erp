package com.atlas.erp.auth.dto;

import com.atlas.erp.users.dto.UserResponse;

/** Resposta de login e de refresh: access token (curto), refresh token (opaco, rotativo) e o usuário. */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user) {
}
