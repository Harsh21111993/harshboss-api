package com.harshboss.repository;

import com.harshboss.entity.SentEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SentEmailRepository extends JpaRepository<SentEmail, UUID> {

    List<SentEmail> findAllByOrderBySentAtDesc();

    List<SentEmail> findByRelatedApprovalIdOrderBySentAtAsc(UUID relatedApprovalId);
}
