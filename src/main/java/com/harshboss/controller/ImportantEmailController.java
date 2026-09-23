package com.harshboss.controller;

import com.harshboss.dto.ImportantEmailDto;
import com.harshboss.repository.ImportantEmailRepository;
import com.harshboss.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Important emails endpoint — lists the lightweight summaries of emails the
 * AI flagged as important.
 *
 * <p>GET /api/important-emails → list of important emails (sorted by score)
 * <br>GET /api/important-emails/stats → count by importance</p>
 *
 * <p>Each email has a {@code providerUrl} — a deep link to view the full
 * email in Gmail/Outlook. We don't store the full body.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/important-emails")
@RequiredArgsConstructor
public class ImportantEmailController {

    private final ImportantEmailRepository importantEmailRepository;
    private final UserService userService;
    private final com.harshboss.dto.DtoMapper dtoMapper;

    /** GET /api/important-emails — list all important emails (sorted by score desc). */
    @GetMapping
    public ResponseEntity<List<ImportantEmailDto>> list() {
        UUID userId = userService.requireCurrentUserId();
        List<ImportantEmailDto> emails = dtoMapper.toImportantEmailDtos(
                importantEmailRepository.findByUserIdOrderByScoreDesc(userId));
        return ResponseEntity.ok(emails);
    }

    /** GET /api/important-emails/stats — count by importance. */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        UUID userId = userService.requireCurrentUserId();
        long total = importantEmailRepository.countByUserId(userId);
        long high = importantEmailRepository.findByUserIdOrderByScoreDesc(userId).stream()
                .filter(e -> e.getImportance() == com.harshboss.entity.enums.Importance.HIGH).count();
        long meetings = importantEmailRepository.findByUserIdOrderByScoreDesc(userId).stream()
                .filter(com.harshboss.entity.ImportantEmail::isMeetingInvitation).count();
        return ResponseEntity.ok(Map.of(
                "total", total,
                "high", high,
                "meetings", meetings
        ));
    }
}
