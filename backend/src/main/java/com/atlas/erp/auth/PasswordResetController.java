package com.atlas.erp.auth;

import com.atlas.erp.auth.dto.PasswordResetConfirmation;
import com.atlas.erp.auth.dto.PasswordResetRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/password-reset")
@Tag(name = "Auth")
@SecurityRequirements
@ConditionalOnProperty(prefix = "app.password-reset", name = "enabled", havingValue = "true")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/request")
    @Operation(summary = "Solicita link de redefinição; a resposta não revela se o e-mail existe")
    public ResponseEntity<Void> request(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/confirm")
    @Operation(summary = "Redefine senha com token temporário de uso único")
    public ResponseEntity<Void> confirm(@Valid @RequestBody PasswordResetConfirmation request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
