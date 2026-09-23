package com.harshboss.controller;

import com.harshboss.dto.UserDto;
import com.harshboss.entity.Resume;
import com.harshboss.service.JobSearchService;
import com.harshboss.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * User profile endpoints — the complete personalization hub.
 *
 * <p>GET    /api/users/exists           → { exists: boolean }
 * <p>GET    /api/users/me               → UserDto
 * <p>POST   /api/users                  → UserDto (create)
 * <p>PATCH  /api/users/me               → UserDto (update name/email/title/avatar/timezone)
 * <p>POST   /api/users/me/profile-pic   → UserDto (upload profile picture)
 * <p>POST   /api/users/me/resume         → Resume (upload resume PDF/DOC/text for AI parsing)
 * <p>GET    /api/users/me/resume         → List<Resume> (get stored resumes)</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JobSearchService jobSearchService;

    @GetMapping("/exists")
    public ResponseEntity<Map<String, Boolean>> exists() {
        return ResponseEntity.ok(Map.of("exists", userService.hasAnyUser()));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me() {
        return ResponseEntity.ok(userService.getCurrentUser());
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@RequestBody Map<String, Object> body) {
        String fullName    = body.get("fullName")    instanceof String s ? s.trim() : "";
        String email       = body.get("email")       instanceof String s ? s.trim() : "";
        String title       = body.get("title")       instanceof String s ? s : null;
        String avatarColor = body.get("avatarColor") instanceof String s ? s : "emerald";
        String timezone    = body.get("timezone")    instanceof String s ? s : "Asia/Calcutta";
        if (fullName.isEmpty() || email.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(userService.createUser(fullName, email, title, avatarColor, timezone));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserDto> updateMe(@RequestBody Map<String, Object> body) {
        String fullName    = body.get("fullName")    instanceof String s ? s : null;
        String email       = body.get("email")       instanceof String s ? s : null;
        String title       = body.get("title")       instanceof String s ? s : null;
        String avatarColor = body.get("avatarColor") instanceof String s ? s : null;
        String timezone    = body.get("timezone")    instanceof String s ? s : null;
        String profilePic  = body.get("profilePic")  instanceof String s ? s : null;
        return ResponseEntity.ok(userService.updateCurrentUserExtended(
                fullName, email, title, avatarColor, timezone, profilePic));
    }

    /** POST /api/users/me/profile-pic — upload a profile picture (multipart file) */
    @PostMapping(value = "/me/profile-pic", consumes = "multipart/form-data")
    public ResponseEntity<UserDto> uploadProfilePic(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String contentType = file.getContentType() != null ? file.getContentType() : "image/png";
            String base64 = Base64.getEncoder().encodeToString(file.getBytes());
            String dataUrl = "data:" + contentType + ";base64," + base64;
            return ResponseEntity.ok(userService.updateProfilePic(dataUrl));
        } catch (IOException e) {
            log.error("Profile pic upload failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /** POST /api/users/me/resume — upload resume (PDF/DOC/TXT) for AI parsing */
    @PostMapping(value = "/me/resume", consumes = "multipart/form-data")
    public ResponseEntity<Resume> uploadResume(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            // Extract text (for now, treat as text; PDF parsing needs Apache PDFBox)
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            Resume resume = jobSearchService.uploadResume(file.getOriginalFilename(), content);
            return ResponseEntity.ok(resume);
        } catch (IOException e) {
            log.error("Resume upload failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /** POST /api/users/me/resume-text — upload resume as raw text */
    @PostMapping("/me/resume-text")
    public ResponseEntity<Resume> uploadResumeText(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        String fileName = body.getOrDefault("fileName", "resume.txt");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Resume resume = jobSearchService.uploadResume(fileName, text);
        return ResponseEntity.ok(resume);
    }

    /** GET /api/users/me/resumes — list stored resumes */
    @GetMapping("/me/resumes")
    public ResponseEntity<?> getResumes() {
        return ResponseEntity.ok(jobSearchService.getResumes());
    }
}
