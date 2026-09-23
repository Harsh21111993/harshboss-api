package com.harshboss.repository;

import com.harshboss.entity.EmailAnalysis;
import com.harshboss.entity.enums.Importance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailAnalysisRepository extends JpaRepository<EmailAnalysis, UUID> {

    Optional<EmailAnalysis> findByEmailId(UUID emailId);

    long countByImportance(Importance importance);

    @Query("select ea from EmailAnalysis ea join fetch ea.email e " +
           "where ea.importance in :importances " +
           "order by e.receivedAt desc")
    List<EmailAnalysis> findImportantWithEmails(@Param("importances") List<Importance> importances);

    // ── User-scoped: only analyses belonging to emails owned by this user ──
    @Query("select ea from EmailAnalysis ea join fetch ea.email e " +
           "where e.userId = :userId and ea.importance in :importances " +
           "order by e.receivedAt desc")
    List<EmailAnalysis> findImportantWithEmailsForUser(@Param("userId") UUID userId,
                                                       @Param("importances") List<Importance> importances);

    long countByEmail_UserIdAndImportance(UUID userId, Importance importance);
}
