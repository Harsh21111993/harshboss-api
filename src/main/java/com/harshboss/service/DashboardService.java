package com.harshboss.service;
import com.harshboss.aspect.annotation.CacheResult;
import com.harshboss.aspect.annotation.LogExecution;
import com.harshboss.aspect.annotation.MeasurePerformance;
import com.harshboss.aspect.annotation.AuditAction;
import java.util.concurrent.TimeUnit;

import com.harshboss.ai.DailyBriefAiService;
import com.harshboss.dto.CalendarEventDto;
import com.harshboss.dto.DailyBriefResponse;
import com.harshboss.dto.DtoMapper;
import com.harshboss.dto.EmailDto;
import com.harshboss.dto.EmailAnalysisDto;
import com.harshboss.dto.StatsResponse;
import com.harshboss.entity.Email;
import com.harshboss.entity.EmailAnalysis;
import com.harshboss.entity.enums.Importance;
import com.harshboss.repository.EmailAnalysisRepository;
import com.harshboss.repository.EmailRepository;
import com.harshboss.repository.ApprovalRepository;
import com.harshboss.entity.enums.ApprovalStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Dashboard aggregates: the four stat cards and the AI Daily Brief.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EmailRepository emailRepository;
    private final EmailAnalysisRepository emailAnalysisRepository;
    private final ApprovalRepository approvalRepository;
    private final CalendarService calendarService;
    private final DailyBriefAiService dailyBriefAiService;
    private final UserService userService;
    private final DtoMapper dtoMapper;

    @Transactional(readOnly = true)
    @CacheResult(ttl = 2, unit = TimeUnit.MINUTES)
    @LogExecution
    public StatsResponse stats() {
        UUID userId = userService.requireCurrentUserId();
        long unread           = emailRepository.countByUserIdAndIsReadFalse(userId);
        long important        = emailAnalysisRepository.countByEmail_UserIdAndImportance(userId, Importance.HIGH);
        long pendingApprovals = approvalRepository.countByUserIdAndStatus(userId, ApprovalStatus.PENDING);
        long meetingsToday    = calendarService.countEventsOnDay(Instant.now());
        return new StatsResponse(unread, important, pendingApprovals, meetingsToday);
    }

    @Transactional(readOnly = true)
    @MeasurePerformance(warnThresholdMs = 15000)
    @AuditAction(action = "DAILY_BRIEF", description = "AI daily briefing generation")
    public DailyBriefResponse dailyBrief() {
        UUID userId = userService.requireCurrentUserId();
        // Today's important emails (HIGH or MEDIUM importance) with their email data.
        List<EmailAnalysis> importantAnalyses = emailAnalysisRepository
                .findImportantWithEmailsForUser(userId, List.of(Importance.HIGH, Importance.MEDIUM));

        List<EmailDto> importantEmails = importantAnalyses.stream()
                .map(ea -> {
                    Email email = ea.getEmail();
                    EmailAnalysisDto analysisDto = dtoMapper.toDto(ea);
                    return new EmailDto(
                            email.getId().toString(),
                            email.getFromAddress(),
                            email.getFromName(),
                            email.getSubject(),
                            email.getBody(),
                            email.getFolder(),
                            email.isRead(),
                            email.getReceivedAt(),
                            analysisDto
                    );
                })
                .toList();

        // Today's calendar events (today's window in UTC).
        Instant now = Instant.now();
        Instant startOfDay = now.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant endOfDay   = startOfDay.plus(java.time.Duration.ofDays(1));
        List<CalendarEventDto> todaysMeetings = calendarService.getUnifiedEvents(startOfDay, endOfDay);

        String brief = dailyBriefAiService.generate(importantEmails, todaysMeetings);
        log.info("Daily brief generated ({} important emails, {} meetings today)",
                importantEmails.size(), todaysMeetings.size());
        return new DailyBriefResponse(brief);
    }
}
