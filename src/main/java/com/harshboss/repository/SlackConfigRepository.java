package com.harshboss.repository;

import com.harshboss.entity.SlackConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SlackConfigRepository extends JpaRepository<SlackConfig, UUID> {
    Optional<SlackConfig> findByUserId(UUID userId);
}
