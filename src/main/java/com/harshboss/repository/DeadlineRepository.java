package com.harshboss.repository;

import com.harshboss.entity.Deadline;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DeadlineRepository extends JpaRepository<Deadline, UUID> {
    List<Deadline> findByUserIdAndStatusOrderByDueAtAsc(UUID userId, String status);
    List<Deadline> findByUserIdOrderByDueAtAsc(UUID userId);
}
