package com.harshboss.repository;

import com.harshboss.entity.Approval;
import com.harshboss.entity.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalRepository extends JpaRepository<Approval, UUID> {

    List<Approval> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);

    long countByStatus(ApprovalStatus status);

    List<Approval> findAllByOrderByCreatedAtDesc();

    // ── User-scoped queries ──
    List<Approval> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndStatus(UUID userId, ApprovalStatus status);
}
