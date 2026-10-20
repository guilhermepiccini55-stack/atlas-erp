package com.atlas.erp.users.dto;

import com.atlas.erp.users.Role;
import com.atlas.erp.users.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Dados não credenciais para editar um usuário existente. */
public record UserRequest(
        @NotBlank(message = "Informe o nome.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String name,

        @NotBlank(message = "Informe o e-mail.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 180, message = "O e-mail deve ter no máximo 180 caracteres.")
        String email,

        @NotNull(message = "Informe o papel do usuário.")
        Role role,

        UserStatus status) {
}
