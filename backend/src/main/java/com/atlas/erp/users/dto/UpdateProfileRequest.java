package com.atlas.erp.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Atualização do próprio perfil. O e-mail é o identificador de login e só pode ser alterado por um administrador. */
public record UpdateProfileRequest(
        @NotBlank(message = "Informe o nome.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String name) {
}
