package com.harshboss.repository;

import com.harshboss.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Look up by email (used for login flows later). */
    Optional<User> findByEmail(String email);
}
