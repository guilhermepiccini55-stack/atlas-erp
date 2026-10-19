package com.atlas.erp.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atlas.erp.common.spec.SpecificationBuilder;
import com.atlas.erp.config.JpaAuditingConfig;
import com.atlas.erp.support.PostgresTestContainer;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/** Roda as migrations Flyway num PostgreSQL real (Testcontainers) e valida mapeamento, auditoria e soft delete. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class UserRepositoryIT extends PostgresTestContainer {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager em;

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private User newUser(String marker, String name, Role role, UserStatus status) {
        return new User(name + " " + marker, name.toLowerCase().replace(' ', '.') + "." + marker + "@atlaserp.com",
                "$2a$10$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz0123", role, status);
    }

    @Test
    void save_populatesIdAndAuditFields() {
        User saved = userRepository.saveAndFlush(newUser(unique(), "Audit User", Role.EMPLOYEE, UserStatus.ACTIVE));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo("system");
    }

    @Test
    void findByEmailIgnoreCase_matchesRegardlessOfCase() {
        String marker = unique();
        User saved = userRepository.saveAndFlush(newUser(marker, "Case User", Role.VIEWER, UserStatus.ACTIVE));

        assertThat(userRepository.findByEmailIgnoreCase(saved.getEmail().toUpperCase())).isPresent();
        assertThat(userRepository.existsByEmailIgnoreCase(saved.getEmail().toUpperCase())).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCaseAndIdNot(saved.getEmail(), saved.getId())).isFalse();
    }

    @Test
    void delete_isSoft_rowRemainsButIsHiddenFromQueries() {
        User saved = userRepository.saveAndFlush(newUser(unique(), "Soft Delete", Role.EMPLOYEE, UserStatus.ACTIVE));
        UUID id = saved.getId();

        userRepository.delete(saved);
        userRepository.flush();
        em.clear();

        assertThat(userRepository.findById(id)).isEmpty();
        Number rows = (Number) em.createNativeQuery("select count(*) from users where id = :id and deleted_at is not null")
                .setParameter("id", id)
                .getSingleResult();
        assertThat(rows.longValue()).isEqualTo(1L);
    }

    @Test
    void email_isUniqueOnlyAmongNonDeletedUsers() {
        User first = userRepository.saveAndFlush(newUser(unique(), "Reuse Email", Role.EMPLOYEE, UserStatus.ACTIVE));
        String email = first.getEmail();

        // duplicado entre ativos: viola o índice único
        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User("Outro", email.toUpperCase(), first.getPasswordHash(), Role.VIEWER, UserStatus.ACTIVE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void email_canBeReusedAfterSoftDelete() {
        User first = userRepository.saveAndFlush(newUser(unique(), "Reuse After Delete", Role.EMPLOYEE, UserStatus.ACTIVE));
        String email = first.getEmail();
        userRepository.delete(first);
        userRepository.flush();
        em.clear();

        User again = userRepository.saveAndFlush(
                new User("Voltou", email, first.getPasswordHash(), Role.VIEWER, UserStatus.ACTIVE));

        assertThat(again.getId()).isNotEqualTo(first.getId());
        assertThat(userRepository.findByEmailIgnoreCase(email)).isPresent();
    }

    @Test
    void specification_filtersBySearchRoleAndStatusWithPagingAndSorting() {
        String marker = unique();
        userRepository.save(newUser(marker, "Alice Filtro", Role.MANAGER, UserStatus.ACTIVE));
        userRepository.save(newUser(marker, "Bruno Filtro", Role.MANAGER, UserStatus.PENDING));
        userRepository.save(newUser(marker, "Carla Filtro", Role.VIEWER, UserStatus.ACTIVE));
        userRepository.flush();

        var all = SpecificationBuilder.<User>of().search(marker, "name", "email").build();
        var managers = SpecificationBuilder.<User>of().search(marker, "name", "email").equal("role", Role.MANAGER).build();
        var activeManagers = SpecificationBuilder.<User>of().search(marker, "name", "email")
                .equal("role", Role.MANAGER).equal("status", UserStatus.ACTIVE).build();

        Page<User> sortedPage = userRepository.findAll(all, PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "name")));

        assertThat(sortedPage.getTotalElements()).isEqualTo(3);
        assertThat(sortedPage.getContent()).hasSize(2);
        assertThat(sortedPage.getContent().get(0).getName()).startsWith("Carla");
        assertThat(userRepository.findAll(managers)).hasSize(2);
        assertThat(userRepository.findAll(activeManagers)).extracting(User::getName).singleElement().asString().startsWith("Alice");
    }

    @Test
    void search_treatsLikeWildcardsLiterally() {
        String marker = unique();
        userRepository.saveAndFlush(newUser(marker, "Literal Percent", Role.EMPLOYEE, UserStatus.ACTIVE));

        var spec = SpecificationBuilder.<User>of().search("%", "name", "email").build();

        assertThat(userRepository.findAll(spec)).isEmpty();
    }

    @Test
    void countByRole_reflectsInsertedUsers() {
        Map<Role, Long> before = roleCounts();
        String marker = unique();
        userRepository.save(newUser(marker, "Guest Um", Role.GUEST, UserStatus.ACTIVE));
        userRepository.save(newUser(marker, "Guest Dois", Role.GUEST, UserStatus.ACTIVE));
        userRepository.saveAndFlush(newUser(marker, "Viewer Um", Role.VIEWER, UserStatus.ACTIVE));

        Map<Role, Long> after = roleCounts();

        assertThat(after.getOrDefault(Role.GUEST, 0L) - before.getOrDefault(Role.GUEST, 0L)).isEqualTo(2L);
        assertThat(after.getOrDefault(Role.VIEWER, 0L) - before.getOrDefault(Role.VIEWER, 0L)).isEqualTo(1L);
    }

    private Map<Role, Long> roleCounts() {
        Map<Role, Long> counts = new HashMap<>();
        userRepository.countByRole().forEach(rc -> counts.put(rc.getRole(), rc.getTotal()));
        return counts;
    }
}
