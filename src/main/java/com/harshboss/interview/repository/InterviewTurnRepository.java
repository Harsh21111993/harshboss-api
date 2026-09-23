package com.harshboss.interview.repository;

import com.harshboss.interview.entity.InterviewTurn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InterviewTurnRepository extends JpaRepository<InterviewTurn, UUID> {
    List<InterviewTurn> findBySessionIdOrderByQuestionNumberAsc(UUID sessionId);

    /** Find recurring weaknesses across all sessions for a user (scored <= 2.5 or appeared >= 2 times). */
    @Query(value = """
        SELECT t.weakness_topic, COUNT(*) as incident_count,
               ROUND(AVG(t.knowledge_score), 2) as avg_score
        FROM interview_turns t
        JOIN interview_sessions s ON t.session_id = s.id
        WHERE s.user_id = :userId AND t.weakness_topic IS NOT NULL
        GROUP BY t.weakness_topic
        HAVING COUNT(*) >= 2 OR AVG(t.knowledge_score) <= 2.5
        ORDER BY incident_count DESC, avg_score ASC
        """, nativeQuery = true)
    List<Object[]> findRecurringWeaknesses(@Param("userId") UUID userId);
}
