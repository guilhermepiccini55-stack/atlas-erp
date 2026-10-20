package com.atlas.erp.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminPasswordChangeRequest(
        @NotBlank(message = "Informe a nova senha.")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
        String newPassword,

        @NotNull(message = "Informe o motivo da alteração.")
        AdminPasswordChangeReason reason) {
}
