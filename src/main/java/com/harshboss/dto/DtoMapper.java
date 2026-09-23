package com.harshboss.dto;

import com.harshboss.entity.Approval;
import com.harshboss.entity.CalendarEvent;
import com.harshboss.entity.Email;
import com.harshboss.entity.EmailAnalysis;
import com.harshboss.entity.ImportantEmail;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Static-ish mappers that convert JPA entities to the DTO records defined in
 * API_CONTRACT.md. Uses {@link JsonUtil} for the JSON-string list columns.
 *
 * <p>Marked as a Spring {@code @Component} so it can be injected, but every
 * method is stateless and could equally be called statically.</p>
 */
@Component
public class DtoMapper {

    public EmailDto toDto(Email email) {
        if (email == null) return null;
        return new EmailDto(
                strId(email.getId()),
                email.getFromAddress(),
                email.getFromName(),
                email.getSubject(),
                email.getBody(),
                email.getFolder(),
                email.isRead(),
                email.getReceivedAt(),
                email.getAnalysis() != null ? toDto(email.getAnalysis()) : null
        );
    }

    public EmailAnalysisDto toDto(EmailAnalysis analysis) {
        if (analysis == null) return null;
        List<String> actionItems = JsonUtil.fromJson(analysis.getActionItems());
        List<String> keyDates = JsonUtil.fromJson(analysis.getKeyDates());
        return new EmailAnalysisDto(
                analysis.getBrief(),
                analysis.getLabel(),
                analysis.getImportance(),
                analysis.getScore(),
                analysis.getReason(),
                analysis.getSuggestedAction(),
                actionItems,
                keyDates
        );
    }

    public CalendarEventDto toDto(CalendarEvent event) {
        if (event == null) return null;
        return new CalendarEventDto(
                strId(event.getId()),
                event.getTitle(),
                event.getPlatform(),
                event.getStartTime(),
                event.getEndTime(),
                event.getOrganizer(),
                JsonUtil.fromJson(event.getAttendees()),
                event.getJoinUrl(),
                event.getLocation(),
                event.isHiddenByOthers(),
                event.getStatus(),
                strId(event.getSourceEmailId()),
                event.getSourceEmailUrl()
        );
    }

    public ImportantEmailDto toDto(ImportantEmail email) {
        if (email == null) return null;
        return new ImportantEmailDto(
                strId(email.getId()),
                email.getProvider(),
                email.getProviderMessageId(),
                email.getProviderUrl(),
                email.getFromAddress(),
                email.getFromName(),
                email.getSubject(),
                email.getBrief(),
                email.getLabel(),
                email.getImportance(),
                email.getScore(),
                email.getReason(),
                email.getSuggestedAction(),
                JsonUtil.fromJson(email.getActionItems()),
                email.getReceivedAt(),
                email.getDetectedAt(),
                email.isMeetingInvitation()
        );
    }

    public List<ImportantEmailDto> toImportantEmailDtos(List<ImportantEmail> emails) {
        if (emails == null) return Collections.emptyList();
        return emails.stream().map(this::toDto).toList();
    }

    public ApprovalDto toDto(Approval approval) {
        if (approval == null) return null;

        ApprovalDto.ProposalDetails details = new ApprovalDto.ProposalDetails(
                approval.getProposalTitle(),
                approval.getProposedStart(),
                approval.getDurationMinutes(),
                approval.getPlatform()
        );

        ApprovalDto.ConflictInfo conflict = null;
        if (approval.getConflictEventTitle() != null) {
            conflict = new ApprovalDto.ConflictInfo(
                    approval.getConflictEventTitle(),
                    approval.getConflictEventStart(),
                    approval.getConflictEventEnd()
            );
        }

        List<String> alternatives = approval.getAlternatives() == null
                ? Collections.emptyList()
                : JsonUtil.fromJson(approval.getAlternatives());

        return new ApprovalDto(
                strId(approval.getId()),
                approval.getType(),
                approval.getRequesterName(),
                approval.getRequesterEmail(),
                approval.getRequestedTime(),
                approval.getStatus(),
                approval.getCreatedAt(),
                approval.getMessage(),
                details,
                conflict,
                alternatives,
                approval.getAutoReplySent(),
                approval.getDecisionEmailSent()
        );
    }

    public List<EmailDto> toEmailDtos(List<Email> emails) {
        if (emails == null) return Collections.emptyList();
        return emails.stream().map(this::toDto).toList();
    }

    public List<CalendarEventDto> toEventDtos(List<CalendarEvent> events) {
        if (events == null) return Collections.emptyList();
        return events.stream().map(this::toDto).toList();
    }

    public List<ApprovalDto> toApprovalDtos(List<Approval> approvals) {
        if (approvals == null) return Collections.emptyList();
        return approvals.stream().map(this::toDto).toList();
    }

    private static String strId(java.util.UUID id) {
        return id == null ? null : id.toString();
    }
}
