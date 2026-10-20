package com.atlas.erp.users;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdminPasswordChangeAuditRepository extends JpaRepository<AdminPasswordChangeAudit, Long> {

    List<AdminPasswordChangeAudit> findAllByAffectedUserId(Long affectedUserId);
}
