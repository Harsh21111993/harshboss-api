package com.harshboss.repository;

import com.harshboss.entity.ThreadSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ThreadSummaryRepository extends JpaRepository<ThreadSummary, UUID> {
    List<ThreadSummary> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
