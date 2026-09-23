package com.harshboss.controller;

import com.harshboss.dto.*;
import com.harshboss.entity.*;
import com.harshboss.service.AdvancedFeaturesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * AdvancedFeaturesController — endpoints for all 10 advanced features.
 *
 * Grouped under /api/advanced/* to keep them organized.
 */
@Slf4j
@RestController
@RequestMapping("/api/advanced")
@RequiredArgsConstructor
public class AdvancedFeaturesController {

    private final AdvancedFeaturesService svc;

    // ═══ 1. AI Reply Drafting ═══
    @PostMapping("/emails/{id}/draft-reply")
    public ResponseEntity<ReplyDraftDto> draftReply(@PathVariable String id,
                                                     @RequestBody Map<String, String> body) {
        String tone = body.getOrDefault("tone", "CUSTOM");
        return ResponseEntity.ok(svc.draftReply(id, tone));
    }

    // ═══ 2. Smart Notifications ═══
    @GetMapping("/notifications")
    public ResponseEntity<List<Notification>> listNotifications() {
        return ResponseEntity.ok(svc.listNotifications());
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount() {
        return ResponseEntity.ok(Map.of("count", svc.unreadCount()));
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable String id) {
        svc.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllRead() {
        svc.markAllRead();
        return ResponseEntity.noContent().build();
    }

    // ═══ 3. Follow-up Reminders (triggered by scheduler, but also manually) ═══
    @PostMapping("/follow-ups/check")
    public ResponseEntity<Map<String, String>> checkFollowUps() {
        svc.checkFollowUps();
        return ResponseEntity.ok(Map.of("message", "Follow-up check completed"));
    }

    // ═══ 4. Meeting Prep Brief ═══
    @PostMapping("/meeting-prep/{eventId}")
    public ResponseEntity<MeetingPrepBrief> generateMeetingPrep(@PathVariable java.util.UUID eventId) {
        MeetingPrepBrief brief = svc.generateMeetingPrep(eventId);
        return brief != null ? ResponseEntity.ok(brief) : ResponseEntity.noContent().build();
    }

    @GetMapping("/meeting-prep")
    public ResponseEntity<List<MeetingPrepBrief>> listMeetingPreps() {
        return ResponseEntity.ok(svc.listMeetingPreps());
    }

    // ═══ 5. Thread Summarization ═══
    @PostMapping("/thread-summary")
    public ResponseEntity<ThreadSummary> summarizeThread(@RequestBody Map<String, String> body) {
        String keyword = body.get("subjectKeyword");
        if (keyword == null || keyword.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(svc.summarizeThread(keyword));
    }

    @GetMapping("/thread-summaries")
    public ResponseEntity<List<ThreadSummary>> listThreadSummaries() {
        return ResponseEntity.ok(svc.listThreadSummaries());
    }

    // ═══ 6. Quick Actions ═══
    @PostMapping("/emails/{id}/quick-action")
    public ResponseEntity<QuickActionResult> quickAction(@PathVariable String id,
                                                          @RequestBody Map<String, String> body) {
        String action = body.get("action");
        return ResponseEntity.ok(svc.quickAction(id, action));
    }

    // ═══ 7. Deadline Tracking ═══
    @GetMapping("/deadlines")
    public ResponseEntity<List<Deadline>> listDeadlines() {
        return ResponseEntity.ok(svc.listDeadlines());
    }

    @PostMapping("/deadlines")
    public ResponseEntity<Deadline> createDeadline(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        String dueAt = body.get("dueAt");
        String sourceSubject = body.get("sourceSubject");
        String sourceUrl = body.get("sourceUrl");
        if (title == null || dueAt == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(svc.createDeadline(title, Instant.parse(dueAt), sourceSubject, sourceUrl));
    }

    @PostMapping("/deadlines/{id}/done")
    public ResponseEntity<Void> markDeadlineDone(@PathVariable String id) {
        svc.markDeadlineDone(id);
        return ResponseEntity.noContent().build();
    }

    // ═══ 8. Slack Integration ═══
    @GetMapping("/slack/config")
    public ResponseEntity<SlackConfigDto> getSlackConfig() {
        SlackConfig c = svc.getSlackConfig();
        return ResponseEntity.ok(c != null
                ? new SlackConfigDto(c.getWebhookUrl(), c.getChannel(), c.isNotifyHighOnly(), c.isAutoForward(), true)
                : new SlackConfigDto(null, null, true, false, false));
    }

    @PostMapping("/slack/config")
    public ResponseEntity<SlackConfigDto> configureSlack(@RequestBody SlackConfigRequest req) {
        SlackConfig c = svc.configureSlack(req.webhookUrl(), req.channel(), req.notifyHighOnly(), req.autoForward());
        return ResponseEntity.ok(new SlackConfigDto(c.getWebhookUrl(), c.getChannel(), c.isNotifyHighOnly(), c.isAutoForward(), true));
    }

    // ═══ 9. Email-to-Task ═══
    @GetMapping("/tasks")
    public ResponseEntity<List<Task>> listTasks() {
        return ResponseEntity.ok(svc.listTasks());
    }

    @PostMapping("/tasks")
    public ResponseEntity<Task> createTask(@RequestBody CreateTaskRequest req) {
        return ResponseEntity.ok(svc.createTask(req));
    }

    @PostMapping("/tasks/{id}/done")
    public ResponseEntity<Void> markTaskDone(@PathVariable String id) {
        svc.markTaskDone(id);
        return ResponseEntity.noContent().build();
    }

    // ═══ 10. Contact Intelligence ═══
    @GetMapping("/contacts")
    public ResponseEntity<List<Contact>> listContacts() {
        return ResponseEntity.ok(svc.listContacts());
    }

    @GetMapping("/contacts/at-risk")
    public ResponseEntity<List<Contact>> listAtRiskContacts() {
        return ResponseEntity.ok(svc.listAtRiskContacts());
    }
}
