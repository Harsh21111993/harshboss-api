package com.harshboss.repository;

import com.harshboss.entity.MeetingPrepBrief;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MeetingPrepBriefRepository extends JpaRepository<MeetingPrepBrief, UUID> {
    List<MeetingPrepBrief> findByUserIdOrderByMeetingStartDesc(UUID userId);
}
