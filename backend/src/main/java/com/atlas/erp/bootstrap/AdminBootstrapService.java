package com.atlas.erp.bootstrap;

import com.atlas.erp.users.Role;
import com.atlas.erp.users.UserRepository;
import com.atlas.erp.users.UserService;
import com.atlas.erp.users.UserStatus;
import com.atlas.erp.users.dto.UserRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminBootstrapService {

    private final AdminBootstrapLock bootstrapLock;
    private final UserRepository userRepository;
    private final UserService userService;
    private final Validator validator;

    public AdminBootstrapService(
            AdminBootstrapLock bootstrapLock,
            UserRepository userRepository,
            UserService userService,
            Validator validator) {
        this.bootstrapLock = bootstrapLock;
        this.userRepository = userRepository;
        this.userService = userService;
        this.validator = validator;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean createFirstAdmin(String name, String email, String password) {
        bootstrapLock.acquire();
        if (userRepository.countAllIncludingDeleted() > 0) {
            return false;
        }

        requireConfiguration(name, email, password);

        UserRequest request = new UserRequest(name, email, password, Role.ADMIN, UserStatus.ACTIVE);
        Set<ConstraintViolation<UserRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new IllegalStateException("Invalid bootstrap administrator configuration: " + details);
        }

        userService.create(request);
        return true;
    }

    private static void requireConfiguration(String name, String email, String password) {
        if (!StringUtils.hasText(name) || !StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "Bootstrap administrator is enabled but BOOTSTRAP_ADMIN_NAME, "
                            + "BOOTSTRAP_ADMIN_EMAIL, and BOOTSTRAP_ADMIN_PASSWORD must all be set.");
        }
    }
}
