package com.harshboss.controller;

import com.harshboss.dto.AnalyzeAllResponse;
import com.harshboss.dto.EmailAnalysisDto;
import com.harshboss.dto.EmailDto;
import com.harshboss.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    /**
     * GET /api/emails?includeSpam=true → EmailDto[]
     */
    @GetMapping
    public ResponseEntity<List<EmailDto>> list(@RequestParam(value = "includeSpam", required = false, defaultValue = "false") boolean includeSpam) {
        List<EmailDto> emails = emailService.listEmails(includeSpam);
        return ResponseEntity.ok(emails);
    }

    /**
     * POST /api/emails/{id}/analyze → EmailAnalysisDto
     */
    @PostMapping("/{id}/analyze")
    public ResponseEntity<EmailAnalysisDto> analyze(@PathVariable UUID id) {
        log.info("POST /api/emails/{}/analyze", id);
        EmailAnalysisDto result = emailService.analyzeEmail(id);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/emails/analyze-all → { results: [...] }
     */
    @PostMapping("/analyze-all")
    public ResponseEntity<AnalyzeAllResponse> analyzeAll(
            @RequestParam(value = "includeSpam", required = false, defaultValue = "true") boolean includeSpam) {
        log.info("POST /api/emails/analyze-all?includeSpam={}", includeSpam);
        AnalyzeAllResponse response = emailService.analyzeAll(includeSpam);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/emails/{id}/read — convenience endpoint to flip the read flag.
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable UUID id,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        boolean read = body == null || !(body.get("read") instanceof Boolean)
                ? true
                : (Boolean) body.get("read");
        emailService.markRead(id, read);
        return ResponseEntity.ok(Map.of("id", id.toString(), "isRead", read));
    }
}
