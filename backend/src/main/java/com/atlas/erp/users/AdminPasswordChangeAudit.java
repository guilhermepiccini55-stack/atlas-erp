package com.atlas.erp.users;

import com.atlas.erp.users.dto.AdminPasswordChangeReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "admin_password_change_audit")
public class AdminPasswordChangeAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "administrator_id", nullable = false)
    private Long administratorId;

    @Column(name = "affected_user_id", nullable = false)
    private Long affectedUserId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdminPasswordChangeReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 32)
    private OperationType operationType = OperationType.ADMIN_PASSWORD_CHANGE;

    protected AdminPasswordChangeAudit() {
    }

    public AdminPasswordChangeAudit(Long administratorId, Long affectedUserId, AdminPasswordChangeReason reason) {
        this.administratorId = administratorId;
        this.affectedUserId = affectedUserId;
        this.reason = reason;
    }

    public Long getAdministratorId() {
        return administratorId;
    }

    public Long getAffectedUserId() {
        return affectedUserId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public AdminPasswordChangeReason getReason() {
        return reason;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public enum OperationType {
        ADMIN_PASSWORD_CHANGE
    }
}
