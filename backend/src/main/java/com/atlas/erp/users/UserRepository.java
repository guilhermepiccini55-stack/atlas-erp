package com.atlas.erp.users;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCaseForUpdate(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Query(value = "SELECT COUNT(*) FROM users", nativeQuery = true)
    long countAllIncludingDeleted();

    long countByCreatedAtLessThan(Instant before);

    long countByStatusAndCreatedAtLessThan(UserStatus status, Instant before);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    @Query("select u.role as role, count(u) as total from User u group by u.role")
    List<RoleCount> countByRole();

    /** Projeção da agregação por papel. */
    interface RoleCount {
        Role getRole();

        long getTotal();
    }
}
