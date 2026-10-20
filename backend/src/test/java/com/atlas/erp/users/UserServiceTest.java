package com.atlas.erp.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.atlas.erp.common.exception.BusinessException;
import com.atlas.erp.common.exception.ResourceNotFoundException;
import com.atlas.erp.auth.RefreshTokenRepository;
import com.atlas.erp.users.dto.AdminPasswordChangeReason;
import com.atlas.erp.users.dto.AdminPasswordChangeRequest;
import com.atlas.erp.users.dto.ChangePasswordRequest;
import com.atlas.erp.users.dto.UserCreateRequest;
import com.atlas.erp.users.dto.UserRequest;
import com.atlas.erp.users.dto.UserResponse;
import com.atlas.erp.users.dto.UserStatsResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AdminPasswordChangeAuditRepository adminPasswordChangeAuditRepository;

    private UserService userService;
    private User existing;
    private Long existingId;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository, new UserMapper(), passwordEncoder, refreshTokenRepository,
                adminPasswordChangeAuditRepository);
        existing = new User("Rafael Costa", "rafael@atlaserp.com", "old-hash", Role.EMPLOYEE, UserStatus.ACTIVE);
        existingId = 42L;
        ReflectionTestUtils.setField(existing, "id", existingId);
    }

    @Test
    void create_normalizesEmailEncodesPasswordAndDefaultsToActive() {
        when(userRepository.existsByEmailIgnoreCase("novo@atlaserp.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.create(
                new UserCreateRequest("  Novo Usuario ", "  NOVO@atlaserp.com ", "Secret123", Role.VIEWER, null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("novo@atlaserp.com");
        assertThat(saved.getName()).isEqualTo("Novo Usuario");
        assertThat(saved.getPasswordHash()).isEqualTo("encoded");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.role()).isEqualTo(Role.VIEWER);
    }

    @Test
    void create_withDuplicateEmail_isConflict() {
        when(userRepository.existsByEmailIgnoreCase("rafael@atlaserp.com")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.create(
                new UserCreateRequest("Rafael", "rafael@atlaserp.com", "Secret123", Role.EMPLOYEE, null)));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(userRepository, never()).save(any());
    }

    @Test
    void create_withoutPassword_isBadRequest() {
        BusinessException ex = assertThrows(BusinessException.class, () -> userService.create(
                new UserCreateRequest("Rafael", "rafael@atlaserp.com", null, Role.EMPLOYEE, null)));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void get_whenMissing_throwsNotFound() {
        Long id = 404L;
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.get(id));
    }

    @Test
    void update_changesFieldsAndKeepsPasswordWhenNotProvided() {
        when(userRepository.findById(existingId)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("rafael@atlaserp.com", existingId)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.update(existingId,
                new UserRequest("Rafael C.", "rafael@atlaserp.com", Role.MANAGER, UserStatus.INACTIVE),
                "admin@atlaserp.com");

        assertThat(response.name()).isEqualTo("Rafael C.");
        assertThat(response.role()).isEqualTo(Role.MANAGER);
        assertThat(response.status()).isEqualTo(UserStatus.INACTIVE);
        assertThat(existing.getPasswordHash()).isEqualTo("old-hash");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void update_ownRole_isBlocked() {
        when(userRepository.findById(existingId)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot(any(), eq(existingId))).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.update(existingId,
                new UserRequest("Rafael", "rafael@atlaserp.com", Role.ADMIN, UserStatus.ACTIVE),
                "rafael@atlaserp.com"));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        verify(userRepository, never()).save(any());
    }

    @Test
    void delete_ownAccount_isBlocked() {
        when(userRepository.findById(existingId)).thenReturn(Optional.of(existing));

        assertThrows(BusinessException.class, () -> userService.delete(existingId, "rafael@atlaserp.com"));

        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void delete_otherUser_softDeletes() {
        when(userRepository.findById(existingId)).thenReturn(Optional.of(existing));

        userService.delete(existingId, "admin@atlaserp.com");

        verify(userRepository).delete(existing);
    }

    @Test
    void changePassword_withWrongCurrentPassword_isBadRequest() {
        when(userRepository.findByEmailIgnoreCase("rafael@atlaserp.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.changePassword(
                "rafael@atlaserp.com", new ChangePasswordRequest("wrong", "NewSecret123")));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void changePassword_withCorrectCurrentPassword_storesNewHash() {
        when(userRepository.findByEmailIgnoreCase("rafael@atlaserp.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("OldSecret1", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("NewSecret123", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewSecret123")).thenReturn("new-hash");

        userService.changePassword("rafael@atlaserp.com", new ChangePasswordRequest("OldSecret1", "NewSecret123"));

        assertThat(existing.getPasswordHash()).isEqualTo("new-hash");
        assertThat(existing.getCredentialVersion()).isEqualTo(1);
        verify(userRepository).save(existing);
        verify(refreshTokenRepository).revokeAllByUserId(existingId);
    }

    @Test
    void ownPasswordChangeFailureRevokingSessionsPropagates() {
        when(userRepository.findByEmailIgnoreCase("rafael@atlaserp.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("OldSecret1", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("NewSecret123", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewSecret123")).thenReturn("new-hash");
        org.mockito.Mockito.doThrow(new IllegalStateException("storage failure"))
                .when(refreshTokenRepository).revokeAllByUserId(existingId);

        assertThrows(IllegalStateException.class, () -> userService.changePassword(
                "rafael@atlaserp.com", new ChangePasswordRequest("OldSecret1", "NewSecret123")));

        verify(userRepository).save(existing);
    }

    @Test
    void update_doesNotChangePassword() {
        when(userRepository.findById(existingId)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("rafael@atlaserp.com", existingId)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.update(existingId,
                new UserRequest("Rafael updated", "rafael@atlaserp.com", Role.EMPLOYEE, UserStatus.ACTIVE),
                "admin@atlaserp.com");

        assertThat(existing.getPasswordHash()).isEqualTo("old-hash");
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void adminPasswordChangeUsesBcryptVersioningAndRevokesRefreshTokens() {
        User actor = new User("Admin", "admin@atlaserp.com", "admin-hash", Role.ADMIN, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(actor, "id", 7L);
        when(userRepository.findByEmailIgnoreCase("admin@atlaserp.com")).thenReturn(Optional.of(actor));
        when(userRepository.findById(existingId)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("NewSecret123")).thenReturn("bcrypt-hash");

        userService.changePasswordAsAdmin(existingId,
                new AdminPasswordChangeRequest("NewSecret123", AdminPasswordChangeReason.USER_REQUEST),
                actor.getEmail());

        assertThat(existing.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(existing.getCredentialState()).isEqualTo(CredentialState.BCRYPT);
        assertThat(existing.getCredentialVersion()).isEqualTo(1);
        verify(userRepository).save(existing);
        verify(refreshTokenRepository).revokeAllByUserId(existingId);
        ArgumentCaptor<AdminPasswordChangeAudit> auditCaptor =
                ArgumentCaptor.forClass(AdminPasswordChangeAudit.class);
        verify(adminPasswordChangeAuditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAdministratorId()).isEqualTo(7L);
        assertThat(auditCaptor.getValue().getAffectedUserId()).isEqualTo(existingId);
        assertThat(auditCaptor.getValue().getReason()).isEqualTo(AdminPasswordChangeReason.USER_REQUEST);
        assertThat(auditCaptor.getValue().getOperationType())
                .isEqualTo(AdminPasswordChangeAudit.OperationType.ADMIN_PASSWORD_CHANGE);
    }

    @Test
    void adminPasswordChangeEncodesNewPasswordWithBcrypt() {
        User actor = new User("Admin", "admin@atlaserp.com", "admin-hash", Role.ADMIN, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(actor, "id", 7L);
        when(userRepository.findByEmailIgnoreCase("admin@atlaserp.com")).thenReturn(Optional.of(actor));
        when(userRepository.findById(existingId)).thenReturn(Optional.of(existing));
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder bcrypt =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        UserService bcryptUserService = new UserService(
                userRepository, new UserMapper(), bcrypt, refreshTokenRepository,
                adminPasswordChangeAuditRepository);

        bcryptUserService.changePasswordAsAdmin(existingId,
                new AdminPasswordChangeRequest("NewSecret123", AdminPasswordChangeReason.USER_REQUEST),
                actor.getEmail());

        assertThat(existing.getPasswordHash()).startsWith("$2");
        assertThat(bcrypt.matches("NewSecret123", existing.getPasswordHash())).isTrue();
    }

    @Test
    void adminPasswordChangeRejectsAdminTargetWithoutChangingAnything() {
        User actor = new User("Admin", "admin@atlaserp.com", "admin-hash", Role.ADMIN, UserStatus.ACTIVE);
        User targetAdmin = new User(
                "Other Admin", "other-admin@atlaserp.com", "target-hash", Role.ADMIN, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(actor, "id", 7L);
        ReflectionTestUtils.setField(targetAdmin, "id", existingId);
        when(userRepository.findByEmailIgnoreCase("admin@atlaserp.com")).thenReturn(Optional.of(actor));
        when(userRepository.findById(existingId)).thenReturn(Optional.of(targetAdmin));

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.changePasswordAsAdmin(
                existingId,
                new AdminPasswordChangeRequest("NewSecret123", AdminPasswordChangeReason.SECURITY_CONCERN),
                actor.getEmail()));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(targetAdmin.getPasswordHash()).isEqualTo("target-hash");
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    void adminPasswordChangeFailureRevokingSessionsPropagates() {
        User actor = new User("Admin", "admin@atlaserp.com", "admin-hash", Role.ADMIN, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(actor, "id", 7L);
        when(userRepository.findByEmailIgnoreCase("admin@atlaserp.com")).thenReturn(Optional.of(actor));
        when(userRepository.findById(existingId)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("NewSecret123")).thenReturn("bcrypt-hash");
        org.mockito.Mockito.doThrow(new IllegalStateException("storage failure"))
                .when(refreshTokenRepository).revokeAllByUserId(existingId);

        assertThrows(IllegalStateException.class, () -> userService.changePasswordAsAdmin(
                existingId,
                new AdminPasswordChangeRequest("NewSecret123", AdminPasswordChangeReason.ACCOUNT_COMPROMISED),
                actor.getEmail()));

        verify(userRepository).save(existing);
    }

    @Test
    void list_rejectsSortingByNonWhitelistedField() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by("passwordHash"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.list(null, null, null, pageable));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(userRepository);
    }

    @Test
    void stats_computesKpisChangeAndRoleDistribution() {
        when(userRepository.countByCreatedAtLessThan(any(Instant.class))).thenReturn(100L, 110L, 120L, 130L, 140L, 150L);
        when(userRepository.countByStatusAndCreatedAtLessThan(eq(UserStatus.ACTIVE), any(Instant.class)))
                .thenReturn(80L, 90L, 100L, 110L, 120L, 132L);
        when(userRepository.countByStatusAndCreatedAtLessThan(eq(UserStatus.PENDING), any(Instant.class)))
                .thenReturn(5L, 6L, 7L, 8L, 10L, 5L);
        when(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(Instant.class), any(Instant.class)))
                .thenReturn(10L, 10L, 10L, 10L, 10L, 15L);
        when(userRepository.countByRole()).thenReturn(List.of(roleCount(Role.ADMIN, 50), roleCount(Role.EMPLOYEE, 100)));

        UserStatsResponse stats = userService.stats();

        assertThat(stats.totalUsers().value()).isEqualTo(150L);
        assertThat(stats.totalUsers().changePercent()).isEqualTo(7.1); // (150-140)/140
        assertThat(stats.totalUsers().sparkline()).containsExactly(100L, 110L, 120L, 130L, 140L, 150L);
        assertThat(stats.activeUsers().value()).isEqualTo(132L);
        assertThat(stats.activeUsers().changePercent()).isEqualTo(10.0); // (132-120)/120
        assertThat(stats.newThisMonth().changePercent()).isEqualTo(50.0);
        assertThat(stats.pendingInvites().changePercent()).isEqualTo(-50.0);
        assertThat(stats.activity()).hasSize(6);
        assertThat(stats.activity().get(5).value()).isEqualTo(132L);

        assertThat(stats.byRole()).hasSize(Role.values().length);
        assertThat(stats.byRole().stream().filter(r -> r.role() == Role.ADMIN).findFirst().orElseThrow().percent())
                .isEqualTo(33.3);
        assertThat(stats.byRole().stream().filter(r -> r.role() == Role.GUEST).findFirst().orElseThrow().count())
                .isZero();
    }

    @Test
    void percentChange_handlesZeroPreviousValue() {
        assertThat(UserService.percentChange(0, 0)).isZero();
        assertThat(UserService.percentChange(5, 0)).isEqualTo(100.0);
        assertThat(UserService.percentChange(8, 10)).isEqualTo(-20.0);
    }

    private static UserRepository.RoleCount roleCount(Role role, long total) {
        return new UserRepository.RoleCount() {
            @Override
            public Role getRole() {
                return role;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
