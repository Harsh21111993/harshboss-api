package com.harshboss.repository;

import com.harshboss.entity.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {
    List<JobApplication> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    List<JobApplication> findByUserIdAndStatusOrderByUpdatedAtDesc(UUID userId, String status);
    Optional<JobApplication> findByUserIdAndJobId(UUID userId, UUID jobId);
}
