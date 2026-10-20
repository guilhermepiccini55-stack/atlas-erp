package com.atlas.erp.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.atlas.erp.users.Role;
import com.atlas.erp.users.UserRepository;
import com.atlas.erp.users.UserService;
import com.atlas.erp.users.UserStatus;
import com.atlas.erp.users.dto.UserCreateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @Mock
    private AdminBootstrapLock bootstrapLock;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;

    private AdminBootstrapService bootstrapService;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        bootstrapService = new AdminBootstrapService(bootstrapLock, userRepository, userService, validator);
    }

    @Test
    void createFirstAdmin_whenUsersExist_doesNotCreateOrModifyUsers() {
        when(userRepository.countAllIncludingDeleted()).thenReturn(1L);

        boolean created = bootstrapService.createFirstAdmin("Initial Admin", "admin@example.test", "LongTestPass123");

        assertThat(created).isFalse();
        verify(bootstrapLock).acquire();
        verify(userRepository).countAllIncludingDeleted();
        verifyNoInteractions(userService);
    }

    @Test
    void createFirstAdmin_whenDatabaseIsEmpty_createsActiveAdminThroughUserService() {
        when(userRepository.countAllIncludingDeleted()).thenReturn(0L);

        boolean created = bootstrapService.createFirstAdmin("Initial Admin", "admin@example.test", "LongTestPass123");

        assertThat(created).isTrue();
        InOrder order = inOrder(bootstrapLock, userRepository, userService);
        order.verify(bootstrapLock).acquire();
        order.verify(userRepository).countAllIncludingDeleted();
        order.verify(userService).create(new UserCreateRequest(
                "Initial Admin", "admin@example.test", "LongTestPass123", Role.ADMIN, UserStatus.ACTIVE));
    }

    @Test
    void createFirstAdmin_withMissingConfigurationFailsWhenDatabaseIsEmpty() {
        when(userRepository.countAllIncludingDeleted()).thenReturn(0L);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> bootstrapService.createFirstAdmin("", "admin@example.test", "LongTestPass123"));

        assertThat(exception.getMessage()).contains("BOOTSTRAP_ADMIN_NAME");
        verify(bootstrapLock).acquire();
        verify(userRepository).countAllIncludingDeleted();
        verifyNoInteractions(userService);
    }

    @Test
    void createFirstAdmin_withMissingConfigurationAndExistingUsersSkipsBootstrap() {
        when(userRepository.countAllIncludingDeleted()).thenReturn(1L);

        boolean created = bootstrapService.createFirstAdmin("", "", "");

        assertThat(created).isFalse();
        verify(bootstrapLock).acquire();
        verify(userRepository).countAllIncludingDeleted();
        verifyNoInteractions(userService);
    }

    @Test
    void createFirstAdmin_withInvalidRequestFailsWhenDatabaseIsEmpty() {
        when(userRepository.countAllIncludingDeleted()).thenReturn(0L);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> bootstrapService.createFirstAdmin("Initial Admin", "not-an-email", "short"));

        assertThat(exception.getMessage()).contains("email").contains("password");
        assertThat(exception.getMessage()).doesNotContain("short");
        verify(bootstrapLock).acquire();
        verify(userRepository).countAllIncludingDeleted();
        verifyNoInteractions(userService);
    }

    @Test
    void createFirstAdmin_whenAdvisoryLockFailsPropagatesAndDoesNotCreateUser() {
        RuntimeException failure = new IllegalStateException("lock unavailable");
        org.mockito.Mockito.doThrow(failure).when(bootstrapLock).acquire();

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> bootstrapService.createFirstAdmin("Initial Admin", "admin@example.test", "LongTestPass123"));

        assertThat(thrown).isSameAs(failure);
        verify(userRepository, never()).countAllIncludingDeleted();
        verifyNoInteractions(userService);
    }

    @Test
    void createFirstAdmin_whenCountingUsersFailsPropagatesAndDoesNotCreateUser() {
        RuntimeException failure = new IllegalStateException("database unavailable");
        when(userRepository.countAllIncludingDeleted()).thenThrow(failure);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> bootstrapService.createFirstAdmin("Initial Admin", "admin@example.test", "LongTestPass123"));

        assertThat(thrown).isSameAs(failure);
        verify(bootstrapLock).acquire();
        verifyNoInteractions(userService);
    }

    @Test
    void createFirstAdmin_whenUserCreationFailsPropagatesFailure() {
        when(userRepository.countAllIncludingDeleted()).thenReturn(0L);
        RuntimeException failure = new IllegalStateException("user creation failed");
        org.mockito.Mockito.doThrow(failure).when(userService).create(any(UserCreateRequest.class));

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> bootstrapService.createFirstAdmin("Initial Admin", "admin@example.test", "LongTestPass123"));

        assertThat(thrown).isSameAs(failure);
        verify(bootstrapLock).acquire();
        verify(userRepository).countAllIncludingDeleted();
    }
}
