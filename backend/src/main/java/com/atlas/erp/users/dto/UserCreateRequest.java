package com.atlas.erp.users.dto;

import com.atlas.erp.users.Role;
import com.atlas.erp.users.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Dados para criar usuário; a senha inicial é codificada antes de persistir. */
public record UserCreateRequest(
        @NotBlank(message = "Informe o nome.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String name,

        @NotBlank(message = "Informe o e-mail.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 180, message = "O e-mail deve ter no máximo 180 caracteres.")
        String email,

        @NotBlank(message = "Informe uma senha.")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
        String password,

        @NotNull(message = "Informe o papel do usuário.")
        Role role,

        UserStatus status) {
}
