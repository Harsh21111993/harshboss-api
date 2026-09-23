package com.harshboss.service;
import com.harshboss.aspect.annotation.LogExecution;
import com.harshboss.aspect.annotation.MeasurePerformance;
import com.harshboss.aspect.annotation.AuditAction;

import com.harshboss.ai.EmailTriageAiService;
import com.harshboss.ai.MeetingInvitationDetector;
import com.harshboss.ai.embedding.EmbeddingService;
import com.harshboss.dto.AnalyzeAllResponse;
import com.harshboss.dto.DtoMapper;
import com.harshboss.dto.EmailAnalysisDto;
import com.harshboss.dto.EmailDto;
import com.harshboss.dto.JsonUtil;
import com.harshboss.entity.CalendarEvent;
import com.harshboss.entity.Email;
import com.harshboss.entity.EmailAnalysis;
import com.harshboss.entity.enums.EventStatus;
import com.harshboss.entity.enums.Platform;
import com.harshboss.repository.CalendarEventRepository;
import com.harshboss.repository.EmailAnalysisRepository;
import com.harshboss.repository.EmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Email business logic: list, get-by-id, AI triage (single + batch), mark read.
 *
 * <p>All queries are scoped to the current workspace user (from {@link UserService}).
 * A user only ever sees their own emails.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailRepository emailRepository;
    private final EmailAnalysisRepository analysisRepository;
    private final EmailTriageAiService triageAiService;
    private final EmbeddingService embeddingService;
    private final MeetingInvitationDetector meetingDetector;
    private final CalendarEventRepository calendarEventRepository;
    private final UserService userService;
    private final DtoMapper dtoMapper;

    @Transactional(readOnly = true)
    public List<EmailDto> listEmails(boolean includeSpam) {
        UUID userId = userService.requireCurrentUserId();
        List<Email> emails = emailRepository.findAllWithAnalysesForUser(userId, includeSpam);
        return dtoMapper.toEmailDtos(emails);
    }

    @Transactional(readOnly = true)
    public Email getEmailEntity(UUID id) {
        return emailRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Email not found: " + id));
    }

    @Transactional
    @LogExecution(level = "INFO")
    @MeasurePerformance(warnThresholdMs = 10000)
    @AuditAction(action = "EMAIL_TRIAGE", description = "AI email triage + embedding + meeting detection")
    public EmailAnalysisDto analyzeEmail(UUID emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new IllegalArgumentException("Email not found: " + emailId));

        EmailAnalysisDto aiResult = triageAiService.analyze(email);

        // Replace any existing analysis (force re-analyze)
        Optional<EmailAnalysis> existing = analysisRepository.findByEmailId(emailId);
        EmailAnalysis analysis = existing.orElseGet(EmailAnalysis::new);

        analysis.setEmail(email);
        analysis.setBrief(aiResult.brief());
        analysis.setLabel(aiResult.label());
        analysis.setImportance(aiResult.importance());
        analysis.setScore(aiResult.score());
        analysis.setReason(aiResult.reason());
        analysis.setSuggestedAction(aiResult.suggestedAction());
        analysis.setActionItems(JsonUtil.toJson(aiResult.actionItems()));
        analysis.setKeyDates(JsonUtil.toJson(aiResult.keyDates()));
        analysis.setAnalyzedAt(Instant.now());

        analysis = analysisRepository.save(analysis);
        email.setAnalysis(analysis);

        // Embed the email for semantic search (Layer 4: Advanced RAG)
        try {
            embeddingService.embedEmail(email);
        } catch (Exception e) {
            log.debug("Embedding failed for email {} (non-blocking): {}", emailId, e.getMessage());
        }

        // Detect meeting/interview invitations → auto-create calendar event
        try {
            detectAndCreateMeetingEvent(email);
        } catch (Exception e) {
            log.debug("Meeting detection failed for email {} (non-blocking): {}", emailId, e.getMessage());
        }

        log.info("Analyzed email {} → label={}, importance={}, score={}",
                emailId, aiResult.label(), aiResult.importance(), aiResult.score());
        return dtoMapper.toDto(analysis);
    }

    /**
     * Detect if the email contains a meeting/interview invitation and, if so,
     * auto-create a calendar event linked back to the source email.
     * Dedup: if an event already exists for this email (sourceEmailId), skip.
     */
    private void detectAndCreateMeetingEvent(Email email) {
        // Dedup: already created an event for this email?
        if (calendarEventRepository.findBySourceEmailId(email.getId()).isPresent()) {
            return;
        }

        MeetingInvitationDetector.MeetingInvitation meeting =
                meetingDetector.detect(email.getSubject(), email.getBody(), email.getFromName());

        if (meeting == null) {
            return;  // Not a meeting invitation — nothing to do
        }

        // Parse the start time (ISO 8601 with offset → Instant)
        Instant start;
        try {
            start = OffsetDateTime.parse(meeting.getStartDateTime()).toInstant();
        } catch (Exception e) {
            log.warn("Could not parse meeting start time '{}': {}", meeting.getStartDateTime(), e.getMessage());
            return;
        }

        int duration = meeting.getDurationMinutes() > 0 ? meeting.getDurationMinutes() : 30;
        Instant end = start.plusSeconds(duration * 60L);

        // Parse the platform
        Platform platform;
        try {
            platform = Platform.valueOf(meeting.getPlatform().toUpperCase());
        } catch (Exception e) {
            platform = Platform.PERSONAL;
        }

        // Create the calendar event linked to the source email
        CalendarEvent event = new CalendarEvent();
        event.setTitle(meeting.getTitle() != null ? meeting.getTitle() : email.getSubject());
        event.setPlatform(platform);
        event.setStartTime(start);
        event.setEndTime(end);
        event.setOrganizer(email.getFromName());
        event.setAttendees(JsonUtil.toJson(List.of(email.getFromName())));
        event.setJoinUrl(meeting.getJoinUrl());
        event.setLocation(meeting.getLocation());
        event.setHiddenByOthers(false);
        event.setStatus(EventStatus.TENTATIVE);  // Tentative until the user confirms
        event.setUserId(email.getUserId());
        event.setSourceEmailId(email.getId());
        calendarEventRepository.save(event);

        log.info("Auto-created calendar event '{}' from email {} (start={}, platform={})",
                event.getTitle(), email.getId(), start, platform);
    }

    @Transactional
    public AnalyzeAllResponse analyzeAll(boolean includeSpam) {
        UUID userId = userService.requireCurrentUserId();
        List<Email> emails = emailRepository.findAllWithAnalysesForUser(userId, includeSpam);
        List<AnalyzeAllResponse.AnalyzeResult> results = new ArrayList<>(emails.size());

        for (Email email : emails) {
            String emailId = email.getId().toString();
            try {
                EmailAnalysisDto dto = analyzeEmail(email.getId());
                results.add(new AnalyzeAllResponse.AnalyzeResult(emailId, dto, null));
            } catch (Exception e) {
                log.warn("analyze-all: failed for email {}: {}", emailId, e.getMessage());
                results.add(new AnalyzeAllResponse.AnalyzeResult(emailId, null, e.getMessage()));
            }
        }
        log.info("analyze-all: processed {} emails for user {}", results.size(), userId);
        return new AnalyzeAllResponse(results);
    }

    @Transactional
    public void markRead(UUID emailId, boolean read) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new IllegalArgumentException("Email not found: " + emailId));
        email.setRead(read);
        emailRepository.save(email);
    }
}
