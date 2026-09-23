package com.harshboss.repository;

import com.harshboss.entity.UserOauthToken;
import com.harshboss.entity.enums.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserOauthTokenRepository extends JpaRepository<UserOauthToken, UUID> {

    Optional<UserOauthToken> findByUserIdAndProvider(UUID userId, OAuthProvider provider);

    List<UserOauthToken> findByUserId(UUID userId);

    long countByUserId(UUID userId);

    void deleteByUserIdAndProvider(UUID userId, OAuthProvider provider);
}
