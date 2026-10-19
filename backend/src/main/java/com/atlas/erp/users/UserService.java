package com.atlas.erp.users;

import com.atlas.erp.common.exception.BusinessException;
import com.atlas.erp.common.exception.ResourceNotFoundException;
import com.atlas.erp.common.spec.SpecificationBuilder;
import com.atlas.erp.users.dto.ChangePasswordRequest;
import com.atlas.erp.users.dto.UpdateProfileRequest;
import com.atlas.erp.users.dto.UserRequest;
import com.atlas.erp.users.dto.UserResponse;
import com.atlas.erp.users.dto.UserStatsResponse;
import com.atlas.erp.users.dto.UserStatsResponse.Kpi;
import com.atlas.erp.users.dto.UserStatsResponse.MonthPoint;
import com.atlas.erp.users.dto.UserStatsResponse.RoleShare;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("name", "email", "role", "status", "createdAt");
    private static final int STATS_MONTHS = 6;

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(String search, Role role, UserStatus status, Pageable pageable) {
        var spec = SpecificationBuilder.<User>of()
                .search(search, "name", "email")
                .equal("role", role)
                .equal("status", status)
                .build();
        Pageable safe = SpecificationBuilder.safePageable(pageable, SORTABLE_FIELDS, Sort.by(Sort.Direction.DESC, "createdAt"));
        return userRepository.findAll(spec, safe).map(userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return userMapper.toResponse(findOrThrow(id));
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            throw new BusinessException("Informe uma senha com pelo menos 8 caracteres para criar o usuário.", HttpStatus.BAD_REQUEST);
        }
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw emailInUse();
        }
        User user = new User(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()),
                request.role(),
                request.status() != null ? request.status() : UserStatus.ACTIVE);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(UUID id, UserRequest request, String actingEmail) {
        User user = findOrThrow(id);
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw emailInUse();
        }
        UserStatus newStatus = request.status() != null ? request.status() : user.getStatus();
        boolean editingSelf = user.getEmail().equalsIgnoreCase(actingEmail);
        if (editingSelf && (request.role() != user.getRole() || newStatus != UserStatus.ACTIVE || !email.equalsIgnoreCase(user.getEmail()))) {
            throw new BusinessException("Você não pode alterar seu próprio e-mail, papel ou status. Peça a outro administrador.");
        }
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setRole(request.role());
        user.setStatus(newStatus);
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id, String actingEmail) {
        User user = findOrThrow(id);
        if (user.getEmail().equalsIgnoreCase(actingEmail)) {
            throw new BusinessException("Você não pode excluir a própria conta.");
        }
        userRepository.delete(user); // soft delete (@SQLDelete)
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(String email) {
        return userMapper.toResponse(findByEmailOrThrow(email));
    }

    @Transactional
    public UserResponse updateMe(String email, UpdateProfileRequest request) {
        User user = findByEmailOrThrow(email);
        user.setName(request.name().trim());
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = findByEmailOrThrow(email);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("A senha atual está incorreta.", HttpStatus.BAD_REQUEST);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException("A nova senha deve ser diferente da atual.", HttpStatus.BAD_REQUEST);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    /**
     * KPIs (valor atual, variação % vs. mês anterior e sparkline de 6 meses), distribuição por papel e série mensal.
     * Séries acumuladas: usuários criados até o fim de cada mês (o histórico de status não é versionado,
     * então "ativos" e "pendentes" usam o status atual dos usuários criados até aquela data).
     */
    @Transactional(readOnly = true)
    public UserStatsResponse stats() {
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        List<YearMonth> months = new ArrayList<>();
        for (int i = STATS_MONTHS - 1; i >= 0; i--) {
            months.add(current.minusMonths(i));
        }

        List<Long> total = new ArrayList<>();
        List<Long> active = new ArrayList<>();
        List<Long> created = new ArrayList<>();
        List<Long> pending = new ArrayList<>();
        for (YearMonth month : months) {
            Instant start = startOf(month);
            Instant end = startOf(month.plusMonths(1));
            total.add(userRepository.countByCreatedAtLessThan(end));
            active.add(userRepository.countByStatusAndCreatedAtLessThan(UserStatus.ACTIVE, end));
            created.add(userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, end));
            pending.add(userRepository.countByStatusAndCreatedAtLessThan(UserStatus.PENDING, end));
        }

        List<MonthPoint> activity = new ArrayList<>();
        for (int i = 0; i < months.size(); i++) {
            activity.add(new MonthPoint(months.get(i).toString(), active.get(i)));
        }

        return new UserStatsResponse(
                kpi(total),
                kpi(active),
                kpi(created),
                kpi(pending),
                roleShares(),
                activity);
    }

    private List<RoleShare> roleShares() {
        Map<Role, Long> counts = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            counts.put(role, 0L);
        }
        userRepository.countByRole().forEach(rc -> counts.put(rc.getRole(), rc.getTotal()));
        long sum = counts.values().stream().mapToLong(Long::longValue).sum();
        return counts.entrySet().stream()
                .map(e -> new RoleShare(e.getKey(), e.getValue(), sum == 0 ? 0.0 : round1(e.getValue() * 100.0 / sum)))
                .toList();
    }

    private static Kpi kpi(List<Long> series) {
        long value = series.get(series.size() - 1);
        long previous = series.get(series.size() - 2);
        return new Kpi(value, percentChange(value, previous), List.copyOf(series));
    }

    static double percentChange(long current, long previous) {
        if (previous == 0) {
            return current == 0 ? 0.0 : 100.0;
        }
        return round1((current - previous) * 100.0 / previous);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static Instant startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Usuário", id));
    }

    private User findByEmailOrThrow(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> ResourceNotFoundException.of("Usuário", email));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static BusinessException emailInUse() {
        return new BusinessException("Já existe um usuário com este e-mail. Use outro e-mail.", HttpStatus.CONFLICT);
    }
}
