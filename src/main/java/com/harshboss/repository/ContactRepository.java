package com.harshboss.repository;

import com.harshboss.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {
    Optional<Contact> findByUserIdAndEmailAddress(UUID userId, String emailAddress);
    List<Contact> findByUserIdOrderByLastContactedAtDesc(UUID userId);
    List<Contact> findByUserIdAndRelationshipHealthIn(UUID userId, List<String> healths);
}
