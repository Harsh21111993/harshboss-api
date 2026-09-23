package com.harshboss.interview.repository;

import com.harshboss.interview.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {
    List<InterviewSession> findByUserIdOrderByStartedAtDesc(UUID userId);
}
