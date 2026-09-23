package com.harshboss.service;

import com.harshboss.entity.SentEmail;
import com.harshboss.repository.SentEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Records (and mock-sends) outbound notification emails. The prototype does
 * not actually deliver via SMTP — it logs the send and persists the body to the
 * {@code sent_emails} table so the UI can surface it.
 *
 * <p>To wire real email, replace {@code log.info("EMAIL SENT ...")} with a
 * Spring Boot {@code JavaMailSender} call (or SendGrid / Mailgun API).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEmailService {

    private final SentEmailRepository sentEmailRepository;

    /**
     * @param toAddress         recipient email
     * @param toName            recipient display name (nullable)
     * @param subject           email subject
     * @param body              email body
     * @param reason            short tag describing why this email was sent (e.g. "conflict-auto-reply")
     * @param relatedApprovalId optional link to the approval that triggered this email
     */
    public SentEmail recordSentEmail(String toAddress,
                                     String toName,
                                     String subject,
                                     String body,
                                     String reason,
                                     UUID relatedApprovalId) {
        SentEmail sent = new SentEmail();
        sent.setToAddress(toAddress);
        sent.setToName(toName);
        sent.setSubject(subject);
        sent.setBody(body);
        sent.setSentAt(Instant.now());
        sent.setReason(reason);
        sent.setRelatedApprovalId(relatedApprovalId);

        log.info("EMAIL SENT to {} <{}>: [{}] (reason={}, relatedApprovalId={})",
                toName == null ? "" : toName, toAddress, subject, reason, relatedApprovalId);

        return sentEmailRepository.save(sent);
    }
}
