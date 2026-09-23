package com.harshboss.dto;

import java.time.Instant;
import java.util.List;

/** DTOs for the 10 advanced features. */

// 1. Notifications
public record NotificationDto(String id, String type, String title, String body, String linkUrl, boolean isRead, Instant createdAt) {}

// 2. Reply Draft (feature 1: AI Reply Drafting)
public record ReplyDraftDto(String tone, String body) {}
public record ReplyDraftRequest(String emailId, String tone) {} // tone: ACCEPT | DECLINE | CUSTOM

// 3. Thread Summary (feature 5)
public record ThreadSummaryDto(String id, String threadSubject, String decided, String pending, String actionNeeded, int emailCount, Instant createdAt) {}

// 4. Task (feature 9: Email-to-Task)
public record TaskDto(String id, String title, String description, String sourceUrl, String externalSystem, String status, String priority, Instant dueAt, Instant createdAt, Instant completedAt) {}
public record CreateTaskRequest(String title, String description, String priority, String importantEmailId, String sourceUrl) {}

// 5. Deadline (feature 7)
public record DeadlineDto(String id, String title, Instant dueAt, String sourceSubject, String sourceUrl, String status, Instant createdAt) {}

// 6. Contact (feature 10)
public record ContactDto(String id, String emailAddress, String name, Instant lastContactedAt, Instant lastRepliedAt, int totalEmails, int totalReplies, String avgResponseHours, String relationshipHealth, String notes) {}

// 7. Slack (feature 8)
public record SlackConfigDto(String webhookUrl, String channel, boolean notifyHighOnly, boolean autoForward, boolean connected) {}
public record SlackConfigRequest(String webhookUrl, String channel, boolean notifyHighOnly, boolean autoForward) {}

// 8. Meeting Prep Brief (feature 4)
public record MeetingPrepBriefDto(String id, String meetingTitle, Instant meetingStart, List<String> attendees, String relevantEmails, String lastMeetingNotes, String agenda, Instant createdAt) {}

// 9. Quick Actions (feature 6)
public record QuickActionRequest(String emailId, String action) {} // action: ACCEPT_MEETING | SEND_AVAILABILITY | FORWARD | SNOOZE | MARK_DONE
public record QuickActionResult(boolean success, String message) {}
