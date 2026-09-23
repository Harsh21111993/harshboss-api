package com.harshboss.controller;

import com.harshboss.entity.JobApplication;
import com.harshboss.entity.Resume;
import com.harshboss.service.JobSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JobsController — resume upload, job search + matching, application tracking.
 */
@Slf4j
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobsController {

    private final JobSearchService jobSearchService;

    @PostMapping(value = "/upload-resume", consumes = "multipart/form-data")
    public ResponseEntity<Resume> uploadResume(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            Resume resume = jobSearchService.uploadResume(file.getOriginalFilename(), content);
            return ResponseEntity.ok(resume);
        } catch (IOException e) {
            log.error("Resume upload failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/upload-resume-text")
    public ResponseEntity<Resume> uploadResumeText(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        String fileName = body.getOrDefault("fileName", "resume.txt");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Resume resume = jobSearchService.uploadResume(fileName, text);
        return ResponseEntity.ok(resume);
    }

    @GetMapping("/resumes")
    public ResponseEntity<List<Resume>> getResumes() {
        return ResponseEntity.ok(jobSearchService.getResumes());
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> searchJobs(
            @RequestParam UUID resumeId,
            @RequestParam(defaultValue = "20") int maxResults) {
        return ResponseEntity.ok(jobSearchService.searchAndMatchJobs(resumeId, maxResults));
    }

    @GetMapping("/tracked")
    public ResponseEntity<List<JobApplication>> getTrackedJobs() {
        return ResponseEntity.ok(jobSearchService.getTrackedJobs());
    }

    @PostMapping("/applications/{id}/status")
    public ResponseEntity<JobApplication> updateStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(jobSearchService.updateApplicationStatus(id, status));
    }
}
