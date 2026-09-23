package com.harshboss.service;

import com.harshboss.dto.UserDto;
import com.harshboss.entity.User;
import com.harshboss.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Returns the "current" workspace user and manages user lifecycle.
 *
 * <p>In production this would read the authenticated principal from the
 * SecurityContext (JWT / OAuth2) and look up the matching User row. For the
 * local demo we simply return the first seeded user — replace
 * {@link #getCurrentUser()} with a real auth lookup when wiring Spring Security.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /** Cached current user id so we don't hit the DB on every call within a request. */
    private UUID cachedUserId;

    /**
     * Set the "current user" for scheduler-driven operations (multi-user sync).
     * In a real app with Spring Security, this would be handled by the SecurityContext.
     * For the prototype, the scheduler calls this before running per-user jobs.
     */
    public void setCurrentUserId(UUID userId) {
        this.cachedUserId = userId;
    }

    /** Clear the cached user id (called after scheduler finishes a per-user batch). */
    public void clearCurrentUser() {
        this.cachedUserId = null;
    }

    /**
     * Return the current workspace user as a DTO.
     * Falls back to the first row in the table if no specific identity is set.
     */
    @Transactional(readOnly = true)
    public UserDto getCurrentUser() {
        User user = findCurrentUserEntity();
        return toDto(user);
    }

    /** Return the current user's UUID (used by all services to scope queries). */
    @Transactional(readOnly = true)
    public UUID requireCurrentUserId() {
        if (cachedUserId != null) return cachedUserId;
        User user = findCurrentUserEntity();
        cachedUserId = user.getId();
        return cachedUserId;
    }

    /** True if at least one user exists in the database (first-run check). */
    @Transactional(readOnly = true)
    public boolean hasAnyUser() {
        return userRepository.count() > 0;
    }

    /** Create a new user (used by the first-run "Create Profile" flow). */
    @Transactional
    public UserDto createUser(String fullName, String email, String title,
                              String avatarColor, String timezone) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("A user with email " + email + " already exists.");
        }
        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setTitle(title);
        user.setAvatarColor(avatarColor == null || avatarColor.isBlank() ? "emerald" : avatarColor);
        user.setTimezone(timezone == null || timezone.isBlank() ? "Asia/Calcutta" : timezone);
        User saved = userRepository.save(user);
        log.info("Created new user: {} ({})", saved.getFullName(), saved.getEmail());
        cachedUserId = saved.getId();
        return toDto(saved);
    }

    /** Update the current user's editable fields (name, title, avatar color). */
    @Transactional
    public UserDto updateCurrentUser(String fullName, String title, String avatarColor) {
        User user = findCurrentUserEntity();
        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }
        if (title != null) {
            user.setTitle(title.trim());
        }
        if (avatarColor != null && !avatarColor.isBlank()) {
            user.setAvatarColor(avatarColor.trim());
        }
        User saved = userRepository.save(user);
        log.info("Updated user profile: {}", saved.getEmail());
        return toDto(saved);
    }

    /** Update the current user's email too (extended profile edit). */
    @Transactional
    public UserDto updateCurrentUserExtended(String fullName, String email, String title,
                                              String avatarColor, String timezone, String profilePic) {
        User user = findCurrentUserEntity();
        if (fullName != null && !fullName.isBlank()) {
            user.setFullName(fullName.trim());
        }
        if (email != null && !email.isBlank()) {
            // Ensure email is unique
            Optional<User> existing = userRepository.findByEmail(email);
            if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Email " + email + " is already in use.");
            }
            user.setEmail(email.trim());
        }
        if (title != null) {
            user.setTitle(title.trim());
        }
        if (avatarColor != null && !avatarColor.isBlank()) {
            user.setAvatarColor(avatarColor.trim());
        }
        if (timezone != null && !timezone.isBlank()) {
            user.setTimezone(timezone.trim());
        }
        if (profilePic != null) {
            user.setProfilePic(profilePic);
        }
        User saved = userRepository.save(user);
        log.info("Updated user profile (extended): {}", saved.getEmail());
        return toDto(saved);
    }

    private User findCurrentUserEntity() {
        // If the scheduler set a specific user id, use it
        if (cachedUserId != null) {
            return userRepository.findById(cachedUserId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Cached user not found: " + cachedUserId));
        }
        // Otherwise fall back to the first user (single-user prototype)
        return userRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No user found in database. Create a profile first via POST /api/users."));
    }

    /** Update the profile picture (base64 data URL). */
    @Transactional
    public UserDto updateProfilePic(String profilePic) {
        User user = findCurrentUserEntity();
        user.setProfilePic(profilePic);
        User saved = userRepository.save(user);
        log.info("Updated profile picture for user: {}", saved.getEmail());
        return toDto(saved);
    }

    private UserDto toDto(User u) {
        return new UserDto(
                u.getId().toString(),
                u.getFullName(),
                u.getEmail(),
                u.getTitle(),
                u.getAvatarColor(),
                u.getTimezone(),
                u.getProfilePic()
        );
    }
}
