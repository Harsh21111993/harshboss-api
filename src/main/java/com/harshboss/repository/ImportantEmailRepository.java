package com.harshboss.repository;

import com.harshboss.entity.ImportantEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportantEmailRepository extends JpaRepository<ImportantEmail, UUID> {

    List<ImportantEmail> findByUserIdOrderByDetectedAtDesc(UUID userId);

    List<ImportantEmail> findByUserIdOrderByScoreDesc(UUID userId);

    long countByUserId(UUID userId);

    /** Dedup: check if we already flagged this email. */
    Optional<ImportantEmail> findByUserIdAndProviderAndProviderMessageId(
            UUID userId, String provider, String providerMessageId);
}
