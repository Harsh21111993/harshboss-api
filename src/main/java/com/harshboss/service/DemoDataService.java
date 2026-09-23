package com.harshboss.service;

import com.harshboss.dto.JsonUtil;
import com.harshboss.entity.Approval;
import com.harshboss.entity.CalendarEvent;
import com.harshboss.entity.Email;
import com.harshboss.entity.enums.*;
import com.harshboss.repository.ApprovalRepository;
import com.harshboss.repository.CalendarEventRepository;
import com.harshboss.repository.EmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Seeds demo data (12 emails + 12 calendar events + 1 pending approval) for
 * the current workspace user. Called via POST /api/admin/seed-demo from the
 * Angular dashboard's "Load demo data" button.
 *
 * <p>Idempotent: if the user already has emails/events, this is a no-op.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoDataService {

    private final EmailRepository emailRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final ApprovalRepository approvalRepository;
    private final UserService userService;

    @Transactional
    public SeedResult seedDemoData() {
        UUID userId = userService.requireCurrentUserId();

        long existingEmails = emailRepository.countByUserId(userId);
        long existingEvents = calendarEventRepository.countByUserId(userId);

        if (existingEmails > 0 || existingEvents > 0) {
            log.info("Seed demo data: user {} already has data ({} emails, {} events) — skipping.",
                    userId, existingEmails, existingEvents);
            return new SeedResult(existingEmails, existingEvents, 0, false);
        }

        int emailsCreated = seedEmails(userId);
        int eventsCreated = seedCalendarEvents(userId);
        int approvalsCreated = seedApproval(userId);

        log.info("Seed demo data: created {} emails, {} events, {} approvals for user {}",
                emailsCreated, eventsCreated, approvalsCreated, userId);

        return new SeedResult(emailsCreated, eventsCreated, approvalsCreated, true);
    }

    private int seedEmails(UUID userId) {
        Instant now = Instant.now();
        int count = 0;

        count += createEmail(userId, "sarah.chen@acme-corp.com", "Sarah Chen",
                "Q3 Budget Approval — need your sign-off by Friday",
                "Hi,\n\nAttached is the Q3 departmental budget for your review. Marketing's paid-media line item jumped 18% and Finance flagged it. I need your sign-off by end of day Friday so we can lock the numbers before the board readout on Monday.\n\nHappy to walk you through the variances — I'm free Thursday after 2pm.\n\nThanks,\nSarah",
                EmailFolder.INBOX, false, now.minus(28, ChronoUnit.MINUTES));

        count += createEmail(userId, "noreply@teams.com", "Microsoft Teams",
                "You missed a meeting: Sprint Review",
                "You missed a meeting that was scheduled in Microsoft Teams.\n\n• Meeting: Sprint Review — Q2 Cycle 6\n• When: Yesterday, 4:00 PM – 4:45 PM\n• Organizer: raj.patel@globalpay.io\n\nThe meeting recording and transcript are available in the channel. Action items have been posted to the Sprint Planning board.",
                EmailFolder.INBOX, false, now.minus(3, ChronoUnit.HOURS));

        count += createEmail(userId, "newsletter@producttea.co", "ProductTea Weekly",
                "This week in Product: 12 frameworks every PM should know",
                "Hey product people,\n\nThis week we breakdown 12 decision-making frameworks every PM should have in their toolkit — from RICE and Kano to Wardley Maps and Pre-mortems. Plus: why Linear's planning philosophy is quietly winning.\n\nRead time: 7 minutes.\n\n— The ProductTea team",
                EmailFolder.INBOX, true, now.minus(6, ChronoUnit.HOURS));

        count += createEmail(userId, "raj.patel@globalpay.io", "Raj Patel",
                "Production incident: payment-service P1 — your on-call",
                "P1 on payment-service. ~3% of card authorizations are returning 503s since 11:42 UTC. We've failed over to the secondary region but the queue is backing up. You're the on-call IC — can you join the incident bridge ASAP?\n\nBridge: teams.microsoft.com/l/meetup-bridge/inc-2041\nStatus page is updated; legal is on standby.\n\nRaj",
                EmailFolder.INBOX, false, now.minus(47, ChronoUnit.MINUTES));

        count += createEmail(userId, "lottery-winner@claim-prize.tk", "Prize Claims Dept.",
                "CONGRATULATIONS YOU WON $2,000,000",
                "DEAR LUCKY WINNER,\n\nYour email address has been selected in the INTERNATIONAL MEGA JACKPOT PROMOTION. You have won USD $2,000,000.00 (TWO MILLION DOLLARS).\n\nTo claim your prize, please send your full name, address, phone number, and a processing fee of $250 USD via Western Union to our claims agent within 48 hours.\n\nCongratulations once again!\n\nMr. Daniel Okoye\nClaims Director",
                EmailFolder.SPAM, false, now.minus(5, ChronoUnit.HOURS));

        // THE BURIED IMPORTANT — Emily Watson partnership, wrongly filed as spam
        count += createEmail(userId, "emily.watson@vertex-partners.com", "Emily Watson",
                "Re: Partnership proposal — following up",
                "Hi,\n\nFollowing up on the partnership proposal I sent over last month — Vertex Partners is still very interested in co-developing an integration between Harsh-Boss and our workflow engine. We've had three inbound enterprise customers ask for exactly this in the last two weeks.\n\nI know Q2 was busy. Could we grab 30 minutes next week to discuss scoping? I'm happy to send a one-pager in advance.\n\nBest,\nEmily Watson\nVP, Strategic Partnerships — Vertex",
                EmailFolder.SPAM, false, now.minus(4, ChronoUnit.HOURS));

        count += createEmail(userId, "noreply@github.com", "GitHub",
                "[acme-platform] PR #1247 needs your review",
                "A pull request requires your review.\n\nRepository: acme-platform/api-gateway\nPR #1247: refactor: extract auth middleware into standalone module\nAuthor: kelly.morgan@acme-corp.com\nFiles changed: 14 (+312, -198)\n\nDescription: Splits the monolithic auth middleware into a composable module and adds integration tests for the JWT rotation path. Targeting the v3.2 release branch.\n\nView PR: github.com/acme-platform/api-gateway/pull/1247",
                EmailFolder.INBOX, false, now.minus(80, ChronoUnit.MINUTES));

        count += createEmail(userId, "hr@acme-corp.com", "ACME People Ops",
                "Updated employee handbook 2025",
                "Hello team,\n\nThe 2025 employee handbook is now available. Key updates this year:\n\n• Refreshed remote-work policy (now 3 days in office, 2 remote)\n• Expanded parental leave to 16 weeks\n• Updated expense guidelines for travel\n\nPlease review by end of month and acknowledge in Workday.\n\nThanks,\nACME People Ops",
                EmailFolder.INBOX, true, now.minus(1, ChronoUnit.DAYS));

        count += createEmail(userId, "receipts@stripe.com", "Stripe",
                "Invoice #INV-2025-0892 paid — $12,400",
                "Your invoice has been paid.\n\nInvoice: INV-2025-0892\nAmount: $12,400.00 USD\nPaid by: visa ending 4242\nPaid at: " + now.minus(2, ChronoUnit.HOURS) + "\n\nDownload receipt: dashboard.stripe.com/invoices/INV-2025-0892",
                EmailFolder.INBOX, false, now.minus(2, ChronoUnit.HOURS));

        count += createEmail(userId, "patel.family@gmail.com", "Mom",
                "Dinner this Sunday?",
                "Hi sweetheart,\n\nAre you free for dinner this Sunday? Your dad is making biryani and your sister is coming over too. Let me know if 7pm works or if you'd prefer lunch.\n\nLove,\nMom",
                EmailFolder.INBOX, false, now.minus(4, ChronoUnit.HOURS));

        count += createEmail(userId, "noreply@linkedin.com", "LinkedIn",
                "You appeared in 14 searches this week",
                "Hi,\n\nYou appeared in 14 searches this week. Most people who found you work at:\n\n• Stripe\n• Datadog\n• Ramp\n\nWant to see who searched for you? Upgrade to Premium.",
                EmailFolder.INBOX, true, now.minus(13, ChronoUnit.HOURS));

        count += createEmail(userId, "tips@cryptomoonshot.xyz", "Crypto Insider",
                "1000x coin insider tip — join now",
                "DON'T MISS OUT! Our latest insider pick is projected to 1000x in the next 7 days. Join our exclusive Telegram channel for the ticker symbol. Only 50 spots left!\n\nThis is not financial advice.",
                EmailFolder.SPAM, false, now.minus(8, ChronoUnit.HOURS));

        return count;
    }

    private int seedCalendarEvents(UUID userId) {
        Instant now = Instant.now();
        Instant today930 = now.truncatedTo(ChronoUnit.DAYS).plus(9, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES);
        int count = 0;

        count += createEvent(userId, "Daily Standup", Platform.TEAMS,
                today930, today930.plus(30, ChronoUnit.MINUTES),
                "Dana White", List.of("Dana White", "Kelly Morgan", "Raj Patel"),
                "https://teams.microsoft.com/l/meetup/standup-daily", null, false, EventStatus.CONFIRMED);

        count += createEvent(userId, "1:1 with Manager (Dana)", Platform.GOOGLE,
                today930.plus(90, ChronoUnit.MINUTES), today930.plus(120, ChronoUnit.MINUTES),
                "Dana White", List.of("Dana White"), null, null, false, EventStatus.CONFIRMED);

        // The "Busy (hidden by others)" block
        count += createEvent(userId, "Busy", Platform.BLOCKED,
                today930.plus(210, ChronoUnit.MINUTES), today930.plus(270, ChronoUnit.MINUTES),
                "(hidden)", List.of(), null, null, true, EventStatus.CONFIRMED);

        count += createEvent(userId, "Customer Demo — Northwind Retail", Platform.ZOOM,
                today930.plus(270, ChronoUnit.MINUTES), today930.plus(330, ChronoUnit.MINUTES),
                "Olivia Brooks", List.of("Olivia Brooks", "Marcus Tan"),
                "https://zoom.us/j/northwind-demo", null, false, EventStatus.CONFIRMED);

        count += createEvent(userId, "Focus block — retry queue PR", Platform.PERSONAL,
                today930.plus(390, ChronoUnit.MINUTES), today930.plus(450, ChronoUnit.MINUTES),
                "You", List.of("You"), null, null, false, EventStatus.CONFIRMED);

        // Tomorrow
        Instant tomorrow10 = today930.plus(1, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES);
        count += createEvent(userId, "Design Review — Billing v2", Platform.TEAMS,
                tomorrow10, tomorrow10.plus(60, ChronoUnit.MINUTES),
                "Mei Lin", List.of("Mei Lin", "Kelly Morgan"), "https://teams.microsoft.com/l/design-review", null, false, EventStatus.CONFIRMED);

        Instant tomorrow15 = today930.plus(1, ChronoUnit.DAYS).plus(5, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES);
        count += createEvent(userId, "Sprint Planning", Platform.GOOGLE,
                tomorrow15, tomorrow15.plus(60, ChronoUnit.MINUTES),
                "Dana White", List.of("Dana White", "Raj Patel", "Kelly Morgan", "Mei Lin"), null, null, false, EventStatus.CONFIRMED);

        // Day after tomorrow
        Instant d2_1230 = today930.plus(2, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS);
        count += createEvent(userId, "Lunch with Emily (Vertex)", Platform.PERSONAL,
                d2_1230, d2_1230.plus(60, ChronoUnit.MINUTES),
                "Emily Watson", List.of("Emily Watson"), null, "Cafe Rouge, downtown", false, EventStatus.CONFIRMED);

        // Day 3
        Instant d3_1700 = today930.plus(3, ChronoUnit.DAYS).plus(7, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES);
        count += createEvent(userId, "All-hands — Q4 Kickoff", Platform.ZOOM,
                d3_1700, d3_1700.plus(60, ChronoUnit.MINUTES),
                "CEO Office", List.of("All"), "https://zoom.us/j/allhands-q4", null, false, EventStatus.CONFIRMED);

        // Day 4 — blocked hidden
        Instant d4_0900 = today930.plus(4, ChronoUnit.DAYS);
        count += createEvent(userId, "Busy", Platform.BLOCKED,
                d4_0900, d4_0900.plus(60, ChronoUnit.MINUTES),
                "(hidden)", List.of(), null, null, true, EventStatus.CONFIRMED);

        // Day 5
        Instant d5_1100 = today930.plus(5, ChronoUnit.DAYS).plus(90, ChronoUnit.MINUTES);
        count += createEvent(userId, "Vendor sync — Datadog renewal", Platform.TEAMS,
                d5_1100, d5_1100.plus(45, ChronoUnit.MINUTES),
                "Marcus Tan", List.of("Marcus Tan"), "https://teams.microsoft.com/l/vendor-datadog", null, false, EventStatus.CONFIRMED);

        // Day 6
        Instant d6_1830 = today930.plus(6, ChronoUnit.DAYS).plus(9, ChronoUnit.HOURS);
        count += createEvent(userId, "Family dinner (Mom's)", Platform.PERSONAL,
                d6_1830, d6_1830.plus(120, ChronoUnit.MINUTES),
                "Mom", List.of("Mom", "Sister"), null, "Mom's house", false, EventStatus.CONFIRMED);

        return count;
    }

    private int seedApproval(UUID userId) {
        Instant now = Instant.now();
        Instant proposed = now.plus(90, ChronoUnit.MINUTES); // today + 1.5h

        Approval approval = new Approval();
        approval.setType("MEETING_PROPOSAL");
        approval.setRequesterName("Olivia Brooks");
        approval.setRequesterEmail("olivia.brooks@northwind-retail.com");
        approval.setRequestedTime(proposed);
        approval.setStatus(ApprovalStatus.PENDING);
        approval.setCreatedAt(now.minus(15, ChronoUnit.MINUTES));
        approval.setMessage("Hi! Following up on our demo — we'd love to scope the integration. Got 45 mins today?");
        approval.setProposalTitle("Northwind integration scoping");
        approval.setProposedStart(proposed);
        approval.setDurationMinutes(45);
        approval.setPlatform(Platform.ZOOM);
        // Conflict with Focus block (16:00-17:00 today)
        Instant focusStart = now.truncatedTo(ChronoUnit.DAYS).plus(16, ChronoUnit.HOURS);
        approval.setConflictEventTitle("Focus block — retry queue PR");
        approval.setConflictEventStart(focusStart);
        approval.setConflictEventEnd(focusStart.plus(60, ChronoUnit.MINUTES));
        approval.setAlternatives(JsonUtil.toJson(List.of(
                focusStart.plus(90, ChronoUnit.MINUTES).toString(),
                focusStart.minus(90, ChronoUnit.MINUTES).toString(),
                focusStart.plus(1, ChronoUnit.DAYS).toString()
        )));
        approval.setAutoReplySent("Hi Olivia,\n\nThanks for reaching out about scoping the Northwind integration — really looking forward to it.\n\nThe 45-minute slot you proposed overlaps with a focus block I have on my calendar, so I won't be able to make that exact time. Could one of these alternatives work instead?\n\n  • Later this afternoon (in ~3 hours)\n  • Earlier this afternoon (in ~1 hour)\n  • Tomorrow same time\n\nIf none of those land, just send back a couple of times that work for you and I'll make one happen.\n\nBest,\nHarsh-Boss Assistant");
        approval.setUserId(userId);
        approvalRepository.save(approval);
        return 1;
    }

    private int createEmail(UUID userId, String from, String fromName, String subject,
                            String body, EmailFolder folder, boolean isRead, Instant receivedAt) {
        Email email = new Email();
        email.setFromAddress(from);
        email.setFromName(fromName);
        email.setSubject(subject);
        email.setBody(body);
        email.setFolder(folder);
        email.setRead(isRead);
        email.setReceivedAt(receivedAt);
        email.setUserId(userId);
        emailRepository.save(email);
        return 1;
    }

    private int createEvent(UUID userId, String title, Platform platform, Instant start, Instant end,
                            String organizer, List<String> attendees, String joinUrl,
                            String location, boolean hidden, EventStatus status) {
        CalendarEvent ev = new CalendarEvent();
        ev.setTitle(title);
        ev.setPlatform(platform);
        ev.setStartTime(start);
        ev.setEndTime(end);
        ev.setOrganizer(organizer);
        ev.setAttendees(JsonUtil.toJson(attendees));
        ev.setJoinUrl(joinUrl);
        ev.setLocation(location);
        ev.setHiddenByOthers(hidden);
        ev.setStatus(status);
        ev.setUserId(userId);
        calendarEventRepository.save(ev);
        return 1;
    }

    /** Result of a seed operation. */
    public record SeedResult(long emails, long events, long approvals, boolean created) {}
}
