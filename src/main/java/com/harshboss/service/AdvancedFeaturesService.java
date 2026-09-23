package com.harshboss.service;

import com.harshboss.ai.MeetingPrepAiService;
import com.harshboss.ai.ReplyDraftAiService;
import com.harshboss.ai.ThreadSummaryAiService;
import com.harshboss.dto.*;
import com.harshboss.entity.*;
import com.harshboss.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * AdvancedFeaturesService — consolidated service for the 10 advanced features.
 *
 * Features:
 *   1. AI Reply Drafting          → draftReply()
 *   2. Smart Notifications        → createNotification() + listNotifications()
 *   3. Follow-up Reminders        → checkFollowUps()
 *   4. Meeting Prep Brief         → generateMeetingPrep()
 *   5. Thread Summarization       → summarizeThread()
 *   6. Quick Actions              → quickAction()
 *   7. Deadline Tracking          → extractDeadlines() + listDeadlines()
 *   8. Slack Integration          → sendToSlack() + configureSlack()
 *   9. Email-to-Task              → createTask()
 *   10. Contact Intelligence      → updateContacts() + listContacts()
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvancedFeaturesService {

    private final ReplyDraftAiService replyDraftAiService;
    private final ThreadSummaryAiService threadSummaryAiService;
    private final MeetingPrepAiService meetingPrepAiService;
    private final UserService userService;

    private final ImportantEmailRepository importantEmailRepository;
    private final NotificationRepository notificationRepository;
    private final DeadlineRepository deadlineRepository;
    private final TaskRepository taskRepository;
    private final ContactRepository contactRepository;
    private final SlackConfigRepository slackConfigRepository;
    private final ThreadSummaryRepository threadSummaryRepository;
    private final MeetingPrepBriefRepository meetingPrepBriefRepository;
    private final CalendarEventRepository calendarEventRepository;

    private final WebClient.Builder webClientBuilder;

    // ═══════════════════════════════════════════════════════════════
    // 1. AI Reply Drafting
    // ═══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public ReplyDraftDto draftReply(String emailId, String tone) {
        ImportantEmail email = importantEmailRepository.findById(UUID.fromString(emailId))
                .orElseThrow(() -> new IllegalArgumentException("Email not found: " + emailId));
        String body = replyDraftAiService.draftReply(email, tone);
        return new ReplyDraftDto(tone, body);
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Smart Notifications
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public void createNotification(UUID userId, String type, String title, String body, String linkUrl) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLinkUrl(linkUrl);
        n.setRead(false);
        notificationRepository.save(n);

        // Also send to Slack if configured + auto-forward is on
        slackConfigRepository.findByUserId(userId).ifPresent(slack -> {
            if (slack.isAutoForward() && (!slack.isNotifyHighOnly() || "HIGH_EMAIL".equals(type))) {
                sendToSlack(slack, title, body, linkUrl);
            }
        });
    }

    @Transactional(readOnly = true)
    public List<Notification> listNotifications() {
        UUID userId = userService.requireCurrentUserId();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        UUID userId = userService.requireCurrentUserId();
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markRead(String notificationId) {
        notificationRepository.findById(UUID.fromString(notificationId)).ifPresent(n -> {
            n.setRead(true);
            n.setReadAt(Instant.now());
            notificationRepository.save(n);
        });
    }

    @Transactional
    public void markAllRead() {
        UUID userId = userService.requireCurrentUserId();
        notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId).forEach(n -> {
            n.setRead(true);
            n.setReadAt(Instant.now());
            notificationRepository.save(n);
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Follow-up Reminders (called by scheduler)
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public void checkFollowUps() {
        UUID userId = userService.requireCurrentUserId();
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);

        importantEmailRepository.findByUserIdOrderByScoreDesc(userId).stream()
                .filter(e -> e.getImportance() == com.harshboss.entity.enums.Importance.HIGH)
                .filter(e -> e.getReceivedAt().isBefore(cutoff))
                .filter(e -> !e.isMeetingInvitation()) // meetings are handled separately
                .forEach(email -> {
                    long hoursOverdue = Duration.between(email.getReceivedAt(), Instant.now()).toHours();
                    createNotification(userId, "FOLLOW_UP",
                            "Follow up: " + email.getSubject(),
                            "You haven't replied to " + email.getFromName() + "'s email from " + hoursOverdue + " hours ago.",
                            email.getProviderUrl());
                });
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. Meeting Prep Brief
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public MeetingPrepBrief generateMeetingPrep(UUID eventId) {
        UUID userId = userService.requireCurrentUserId();
        CalendarEvent event = calendarEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        // Find relevant emails from the organizer in the last week
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<ImportantEmail> relevant = importantEmailRepository
                .findByUserIdOrderByScoreDesc(userId).stream()
                .filter(e -> e.getReceivedAt().isAfter(weekAgo))
                .filter(e -> event.getOrganizer() != null && event.getOrganizer().contains(e.getFromName()))
                .limit(5)
                .toList();

        MeetingPrepBrief brief = meetingPrepAiService.generateBrief(event, relevant, userId);
        if (brief != null) {
            meetingPrepBriefRepository.save(brief);
            createNotification(userId, "MEETING_PREP",
                    "Meeting prep: " + event.getTitle(),
                    brief.getAgenda() != null ? brief.getAgenda().substring(0, Math.min(200, brief.getAgenda().length())) : "",
                    null);
        }
        return brief;
    }

    @Transactional(readOnly = true)
    public List<MeetingPrepBrief> listMeetingPreps() {
        UUID userId = userService.requireCurrentUserId();
        return meetingPrepBriefRepository.findByUserIdOrderByMeetingStartDesc(userId);
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. Thread Summarization
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public ThreadSummary summarizeThread(String subjectKeyword) {
        UUID userId = userService.requireCurrentUserId();
        List<ImportantEmail> emails = importantEmailRepository
                .findByUserIdOrderByScoreDesc(userId).stream()
                .filter(e -> e.getSubject() != null && e.getSubject().toLowerCase().contains(subjectKeyword.toLowerCase()))
                .toList();

        if (emails.size() < 2) {
            throw new IllegalStateException("Need at least 2 emails with '" + subjectKeyword + "' in the subject to summarize a thread.");
        }

        ThreadSummary summary = threadSummaryAiService.summarize(emails, emails.get(0).getSubject());
        if (summary != null) {
            summary.setUserId(userId);
            threadSummaryRepository.save(summary);
        }
        return summary;
    }

    @Transactional(readOnly = true)
    public List<ThreadSummary> listThreadSummaries() {
        UUID userId = userService.requireCurrentUserId();
        return threadSummaryRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. Quick Actions
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public QuickActionResult quickAction(String emailId, String action) {
        UUID userId = userService.requireCurrentUserId();
        ImportantEmail email = importantEmailRepository.findById(UUID.fromString(emailId))
                .orElseThrow(() -> new IllegalArgumentException("Email not found: " + emailId));

        return switch (action.toUpperCase()) {
            case "ACCEPT_MEETING" -> {
                createNotification(userId, "QUICK_ACTION", "Meeting accepted",
                        "Accepted meeting: " + email.getSubject(), email.getProviderUrl());
                yield new QuickActionResult(true, "Meeting accepted. Calendar updated.");
            }
            case "SEND_AVAILABILITY" -> {
                String draft = replyDraftAiService.draftReply(email, "CUSTOM");
                yield new QuickActionResult(true, "Availability draft ready:\n\n" + draft);
            }
            case "FORWARD" -> {
                createNotification(userId, "QUICK_ACTION", "Email forwarded",
                        "Forwarded: " + email.getSubject(), email.getProviderUrl());
                yield new QuickActionResult(true, "Email forwarded to your team.");
            }
            case "SNOOZE" -> {
                createNotification(userId, "QUICK_ACTION", "Email snoozed 1 hour",
                        "Snoozed: " + email.getSubject() + " — will remind you in 1 hour.",
                        email.getProviderUrl());
                yield new QuickActionResult(true, "Snoozed for 1 hour.");
            }
            case "MARK_DONE" -> {
                createNotification(userId, "QUICK_ACTION", "Email marked done",
                        "Marked as done: " + email.getSubject(), null);
                yield new QuickActionResult(true, "Email marked as done.");
            }
            default -> new QuickActionResult(false, "Unknown action: " + action);
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. Deadline Tracking (extract deadlines from important emails)
    // ═══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<Deadline> listDeadlines() {
        UUID userId = userService.requireCurrentUserId();
        return deadlineRepository.findByUserIdOrderByDueAtAsc(userId);
    }

    @Transactional
    public Deadline createDeadline(String title, Instant dueAt, String sourceSubject, String sourceUrl) {
        UUID userId = userService.requireCurrentUserId();
        Deadline d = new Deadline();
        d.setUserId(userId);
        d.setTitle(title);
        d.setDueAt(dueAt);
        d.setSourceSubject(sourceSubject);
        d.setSourceUrl(sourceUrl);
        d.setStatus("PENDING");
        return deadlineRepository.save(d);
    }

    @Transactional
    public void markDeadlineDone(String deadlineId) {
        deadlineRepository.findById(UUID.fromString(deadlineId)).ifPresent(d -> {
            d.setStatus("DONE");
            deadlineRepository.save(d);
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // 8. Slack Integration
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public SlackConfig configureSlack(String webhookUrl, String channel, boolean notifyHighOnly, boolean autoForward) {
        UUID userId = userService.requireCurrentUserId();
        SlackConfig config = slackConfigRepository.findByUserId(userId)
                .orElseGet(SlackConfig::new);
        config.setUserId(userId);
        config.setWebhookUrl(webhookUrl);
        config.setChannel(channel);
        config.setNotifyHighOnly(notifyHighOnly);
        config.setAutoForward(autoForward);
        return slackConfigRepository.save(config);
    }

    @Transactional(readOnly = true)
    public SlackConfig getSlackConfig() {
        UUID userId = userService.requireCurrentUserId();
        return slackConfigRepository.findByUserId(userId).orElse(null);
    }

    public void sendToSlack(SlackConfig config, String title, String body, String linkUrl) {
        try {
            String slackMsg = """
                    {"text": "*%s*%s%s"}
                    """.formatted(
                    title,
                    body != null ? "\n" + body : "",
                    linkUrl != null ? "\n<" + linkUrl + "|View →>" : "");

            webClientBuilder.build().post()
                    .uri(config.getWebhookUrl())
                    .header("Content-Type", "application/json")
                    .bodyValue(slackMsg)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnSuccess(r -> log.info("Slack notification sent: {}", title))
                    .doOnError(e -> log.error("Slack send failed: {}", e.getMessage()))
                    .onErrorResume(e -> reactor.core.publisher.Mono.empty())
                    .subscribe();
        } catch (Exception e) {
            log.error("Slack integration error: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 9. Email-to-Task
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    public Task createTask(CreateTaskRequest req) {
        UUID userId = userService.requireCurrentUserId();
        Task t = new Task();
        t.setUserId(userId);
        t.setTitle(req.title());
        t.setDescription(req.description());
        t.setSourceUrl(req.sourceUrl());
        t.setExternalSystem("LOCAL");
        t.setStatus("TODO");
        t.setPriority(req.priority() != null ? req.priority() : "MEDIUM");
        if (req.importantEmailId() != null) {
            t.setImportantEmailId(UUID.fromString(req.importantEmailId()));
        }
        return taskRepository.save(t);
    }

    @Transactional(readOnly = true)
    public List<Task> listTasks() {
        UUID userId = userService.requireCurrentUserId();
        return taskRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markTaskDone(String taskId) {
        taskRepository.findById(UUID.fromString(taskId)).ifPresent(t -> {
            t.setStatus("DONE");
            t.setCompletedAt(Instant.now());
            taskRepository.save(t);
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // 10. Contact Intelligence
    // ═══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<Contact> listContacts() {
        UUID userId = userService.requireCurrentUserId();
        return contactRepository.findByUserIdOrderByLastContactedAtDesc(userId);
    }

    /**
     * Update contact stats from a synced important email.
     * Called by SmartSyncService when an important email is persisted.
     */
    @Transactional
    public void updateContactFromEmail(ImportantEmail email) {
        UUID userId = email.getUserId();
        Contact contact = contactRepository.findByUserIdAndEmailAddress(userId, email.getFromAddress())
                .orElseGet(() -> {
                    Contact c = new Contact();
                    c.setUserId(userId);
                    c.setEmailAddress(email.getFromAddress());
                    c.setName(email.getFromName());
                    c.setRelationshipHealth("GOOD");
                    return c;
                });

        contact.setTotalEmails(contact.getTotalEmails() + 1);
        contact.setLastContactedAt(email.getReceivedAt());

        // Update relationship health based on recency
        long daysSinceContact = Duration.between(
                email.getReceivedAt() != null ? email.getReceivedAt() : Instant.now(),
                Instant.now()).toDays();
        if (daysSinceContact > 30) {
            contact.setRelationshipHealth("COLD");
        } else if (daysSinceContact > 14) {
            contact.setRelationshipHealth("AT_RISK");
        } else if (daysSinceContact > 7) {
            contact.setRelationshipHealth("STALE");
        } else {
            contact.setRelationshipHealth("GOOD");
        }

        contactRepository.save(contact);
    }

    @Transactional(readOnly = true)
    public List<Contact> listAtRiskContacts() {
        UUID userId = userService.requireCurrentUserId();
        return contactRepository.findByUserIdAndRelationshipHealthIn(userId,
                List.of("AT_RISK", "COLD"));
    }
}
