package com.harshboss.repository;

import com.harshboss.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Task> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);
}
