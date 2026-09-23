package com.harshboss.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The workspace owner. In production this is populated by OAuth2/JWT login;
 * for the local demo a single row is seeded by Flyway (V3__users.sql) and
 * exposed via GET /api/users/me.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    private String title;

    @Column(name = "avatar_color", nullable = false)
    private String avatarColor;

    @Column(nullable = false)
    private String timezone;

    /** Base64 data URL for the profile picture (e.g. "data:image/png;base64,...") */
    @Column(name = "profile_pic", columnDefinition = "TEXT")
    private String profilePic;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
