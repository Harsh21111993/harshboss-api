package com.harshboss.repository;

import com.harshboss.entity.Email;
import com.harshboss.entity.enums.EmailFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EmailRepository extends JpaRepository<Email, UUID> {

    List<Email> findByFolderOrderByReceivedAtDesc(EmailFolder folder);

    // Eagerly load the optional analysis in a single round-trip.
    @Query("select e from Email e left join fetch e.analysis order by e.receivedAt desc")
    List<Email> findAllWithAnalyses();

    // Filtered by user + optional spam inclusion.
    @Query("select e from Email e left join fetch e.analysis " +
           "where e.userId = :userId " +
           "and (:includeSpam = true or e.folder <> com.harshboss.entity.enums.EmailFolder.SPAM) " +
           "order by e.receivedAt desc")
    List<Email> findAllWithAnalysesForUser(@Param("userId") UUID userId,
                                           @Param("includeSpam") boolean includeSpam);

    long countByUserIdAndIsReadFalse(UUID userId);

    long countByUserId(UUID userId);

    // Legacy (no user filter) — kept for backward compat, prefer the forUser variants.
    @Query("select e from Email e where e.isRead = false order by e.receivedAt desc")
    List<Email> findUnread();

    long countByIsReadFalse();

    @Query("select e from Email e left join fetch e.analysis " +
           "where (:includeSpam = true or e.folder <> com.harshboss.entity.enums.EmailFolder.SPAM) " +
           "order by e.receivedAt desc")
    List<Email> findAllWithAnalyses(@Param("includeSpam") boolean includeSpam);
}
