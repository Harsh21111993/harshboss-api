package com.harshboss.entity;

import com.harshboss.entity.enums.OAuthProvider;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores OAuth2 access + refresh tokens granted by Microsoft (Graph) or Google
 * (Gmail + Calendar) so the app can fetch REAL emails from the user's mailbox.
 *
 * <p>One row per (user, provider) pair. {@code refreshToken} may be null if the
 * provider didn't return one (rare — we request {@code offline_access} /
 * {@code access_type=offline} to force it). When {@code accessToken} expires,
 * {@link com.harshboss.service.OAuth2Service#getValidToken} refreshes it using the
 * stored refresh token and updates this row.</p>
 */
@Entity
@Table(name = "user_oauth_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserOauthToken {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OAuthProvider provider;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_type", nullable = false)
    private String tokenType = "Bearer";

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(columnDefinition = "TEXT")
    private String scope;

    @Column(name = "email_address")
    private String emailAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** True if the access token is expired (with a 60-second safety margin). */
    public boolean isExpired() {
        return expiresAt == null || Instant.now().plusSeconds(60).isAfter(expiresAt);
    }
}
