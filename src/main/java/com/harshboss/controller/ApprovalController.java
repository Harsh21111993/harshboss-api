package com.harshboss.controller;

import com.harshboss.dto.ApprovalDto;
import com.harshboss.dto.DecisionRequest;
import com.harshboss.dto.DecisionResponse;
import com.harshboss.service.ApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    /**
     * GET /api/approvals → ApprovalDto[]
     */
    @GetMapping
    public ResponseEntity<List<ApprovalDto>> list() {
        return ResponseEntity.ok(approvalService.list());
    }

    /**
     * POST /api/approvals/{id}/decide → DecisionResponse
     */
    @PostMapping("/{id}/decide")
    public ResponseEntity<DecisionResponse> decide(@PathVariable UUID id,
                                                   @Valid @RequestBody DecisionRequest req) {
        log.info("POST /api/approvals/{}/decide — decision={}", id, req.decision());
        DecisionResponse response = approvalService.decide(id, req.decision());
        return ResponseEntity.ok(response);
    }
}
