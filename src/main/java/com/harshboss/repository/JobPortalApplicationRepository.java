package com.harshboss.repository;

import com.harshboss.entity.JobPortalApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JobPortalApplicationRepository extends JpaRepository<JobPortalApplication, UUID> {
    List<JobPortalApplication> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    List<JobPortalApplication> findByUserIdAndPortalOrderByUpdatedAtDesc(UUID userId, String portal);
    List<JobPortalApplication> findByUserIdAndStatusOrderByUpdatedAtDesc(UUID userId, String status);
    long countByUserIdAndPortal(UUID userId, String portal);
    long countByUserIdAndStatus(UUID userId, String status);
}
