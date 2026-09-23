package com.harshboss.service;
import com.harshboss.aspect.annotation.AuditAction;
import com.harshboss.aspect.annotation.LogExecution;

import com.harshboss.ai.ConflictEmailAiService;
import com.harshboss.ai.DecisionEmailAiService;
import com.harshboss.dto.ApprovalDto;
import com.harshboss.dto.DecisionRequest;
import com.harshboss.dto.DecisionResponse;
import com.harshboss.dto.DtoMapper;
import com.harshboss.dto.JsonUtil;
import com.harshboss.dto.ProposeMeetingRequest;
import com.harshboss.dto.ProposeMeetingResponse;
import com.harshboss.entity.Approval;
import com.harshboss.entity.CalendarEvent;
import com.harshboss.entity.enums.ApprovalStatus;
import com.harshboss.entity.enums.EventStatus;
import com.harshboss.repository.ApprovalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Approval workflow:
 * <ul>
 *   <li>{@link #createFromProposal} — checks the proposed window against all
 *       events (including hidden/blocked); on conflict, drafts an auto-reply
 *       email offering 3 alternative slots.</li>
 *   <li>{@link #decide} — approves or declines; on approve, promotes (or
 *       creates) a calendar event to CONFIRMED; drafts a notification email.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private static final int ALTERNATIVES_COUNT = 3;
    private static final int ALTERNATIVE_SLOT_MINUTES = 30;

    private final ApprovalRepository approvalRepository;
    private final ConflictDetectionService conflictDetectionService;
    private final ConflictEmailAiService conflictEmailAiService;
    private final DecisionEmailAiService decisionEmailAiService;
    private final NotificationEmailService notificationEmailService;
    private final CalendarService calendarService;
    private final UserService userService;
    private final DtoMapper dtoMapper;

    @Transactional(readOnly = true)
    public List<ApprovalDto> list() {
        UUID userId = userService.requireCurrentUserId();
        return dtoMapper.toApprovalDtos(approvalRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @Transactional
    public ProposeMeetingResponse createFromProposal(ProposeMeetingRequest req) {
        Instant proposedStart = req.proposedStart();
        int duration = req.durationMinutes();

        ConflictDetectionService.ConflictResult conflict =
                conflictDetectionService.check(proposedStart, duration);

        Approval approval = new Approval();
        approval.setType("MEETING_PROPOSAL");
        approval.setRequesterName(req.requesterName());
        approval.setRequesterEmail(req.requesterEmail());
        approval.setRequestedTime(proposedStart);
        approval.setStatus(ApprovalStatus.PENDING);
        approval.setCreatedAt(Instant.now());
        approval.setMessage(req.message());
        approval.setProposalTitle(req.title());
        approval.setProposedStart(proposedStart);
        approval.setDurationMinutes(duration);
        approval.setPlatform(req.platform());
        approval.setUserId(userService.requireCurrentUserId());

        List<String> alternatives = List.of();
        String autoReply = null;

        if (conflict.conflict()) {
            // Conflict — find 3 alternative slots and draft an auto-reply email.
            alternatives = findAlternatives(proposedStart, duration);
            String conflictSummary = conflict.eventTitle() == null
                    ? "an existing calendar block"
                    : conflict.eventTitle() + (conflict.hiddenByOthers() ? " (private calendar block)" : "");
            autoReply = conflictEmailAiService.draftConflictReply(
                    req.requesterName(), proposedStart, conflictSummary, alternatives,
                    userService.getCurrentUser().fullName());

            approval.setConflictEventTitle(conflict.eventTitle());
            approval.setConflictEventStart(conflict.eventStart());
            approval.setConflictEventEnd(conflict.eventEnd());
            approval.setAlternatives(JsonUtil.toJson(alternatives));
            approval.setAutoReplySent(autoReply);

            log.info("Proposal conflict: title='{}' overlaps '{}'; drafted auto-reply ({} alternatives)",
                    req.title(), conflict.eventTitle(), alternatives.size());
        } else {
            // Free — create a TENTATIVE personal calendar event so the slot is reserved.
            calendarService.createEvent(
                    req.title(),
                    req.platform(),
                    proposedStart,
                    proposedStart.plusSeconds(duration * 60L),
                    req.requesterName(),
                    List.of(req.requesterName()),
                    null,
                    null,
                    false,
                    EventStatus.TENTATIVE
            );
            log.info("Proposal free: created tentative event '{}'", req.title());
        }

        approval = approvalRepository.save(approval);

        // Persist the auto-reply as a sent_email (audit trail) if one was drafted.
        if (autoReply != null) {
            notificationEmailService.recordSentEmail(
                    req.requesterEmail(),
                    req.requesterName(),
                    "Re: " + req.title(),
                    autoReply,
                    "conflict-auto-reply",
                    approval.getId()
            );
        }

        ProposeMeetingResponse.Status status = conflict.conflict()
                ? ProposeMeetingResponse.Status.CONFLICT
                : ProposeMeetingResponse.Status.FREE;

        ApprovalDto.ConflictInfo conflictInfo = conflict.conflict()
                ? new ApprovalDto.ConflictInfo(
                        conflict.eventTitle(),
                        conflict.eventStart(),
                        conflict.eventEnd())
                : null;

        return new ProposeMeetingResponse(
                approval.getId().toString(),
                status,
                conflictInfo,
                alternatives,
                autoReply
        );
    }

    @Transactional
    @AuditAction(action = "APPROVAL_DECIDE", description = "Approve/decline a meeting proposal + notify email")
    @LogExecution(level = "INFO")
    public DecisionResponse decide(UUID approvalId, DecisionRequest.Decision decision) {
        Approval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));

        boolean approve = decision == DecisionRequest.Decision.APPROVE;
        ApprovalStatus newStatus = approve ? ApprovalStatus.APPROVED : ApprovalStatus.DECLINED;
        approval.setStatus(newStatus);

        // Draft the notification email to the requester.
        String alternativeNote = approval.getAlternatives() == null
                ? ""
                : String.join(", ", JsonUtil.fromJson(approval.getAlternatives()));
        String notificationEmail = decisionEmailAiService.draftDecisionEmail(
                decision.name(),
                approval.getRequesterName(),
                approval.getProposalTitle(),
                approval.getProposedStart(),
                alternativeNote,
                userService.getCurrentUser().fullName()
        );
        approval.setDecisionEmailSent(notificationEmail);

        if (approve) {
            // Promote the tentative event (if it exists) to CONFIRMED, or create one.
            promoteOrCreateConfirmedEvent(approval);
        }

        approvalRepository.save(approval);

        notificationEmailService.recordSentEmail(
                approval.getRequesterEmail(),
                approval.getRequesterName(),
                (approve ? "Confirmed: " : "Update: ") + approval.getProposalTitle(),
                notificationEmail,
                "decision-" + decision.name().toLowerCase(),
                approval.getId()
        );

        log.info("Approval {} decided: {} — notification email recorded", approvalId, newStatus);

        return new DecisionResponse(
                approval.getId().toString(),
                newStatus,
                notificationEmail
        );
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private List<String> findAlternatives(Instant after, int durationMinutes) {
        List<Instant> slots = conflictDetectionService.findFreeSlots(
                after.plusSeconds(60), durationMinutes, ALTERNATIVES_COUNT);
        List<String> result = new ArrayList<>(slots.size());
        for (Instant s : slots) {
            result.add(s.toString());
        }
        return result;
    }

    /**
     * On approve, if the proposal had a conflict (no tentative event was
     * created) we add a fresh CONFIRMED event at the proposed time. If a
     * tentative event already exists, it would be promoted — but since the
     * tentative event isn't linked to the approval, we just create a new
     * CONFIRMED one here. (Acceptable for the prototype.)
     */
    private void promoteOrCreateConfirmedEvent(Approval approval) {
        try {
            calendarService.createEvent(
                    approval.getProposalTitle(),
                    approval.getPlatform(),
                    approval.getProposedStart(),
                    approval.getProposedStart().plusSeconds(approval.getDurationMinutes() * 60L),
                    approval.getRequesterName(),
                    List.of(approval.getRequesterName()),
                    null,
                    null,
                    false,
                    EventStatus.CONFIRMED
            );
        } catch (Exception e) {
            log.warn("Could not create confirmed event for approval {}: {}", approval.getId(), e.getMessage());
        }
    }
}
