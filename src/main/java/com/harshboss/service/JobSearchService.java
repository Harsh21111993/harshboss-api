package com.harshboss.service;
import com.harshboss.aspect.annotation.AuditAction;
import com.harshboss.aspect.annotation.MeasurePerformance;
import com.harshboss.aspect.annotation.LogExecution;

import com.harshboss.ai.ResumeParserAiService;
import com.harshboss.dto.JsonUtil;
import com.harshboss.entity.Job;
import com.harshboss.entity.JobApplication;
import com.harshboss.entity.Resume;
import com.harshboss.repository.JobApplicationRepository;
import com.harshboss.repository.JobRepository;
import com.harshboss.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * JobSearchService — orchestrates resume parsing, job search (via MCP client),
 * semantic matching, and application tracking.
 *
 * <p>The job search itself is done by the atlas-mcp-server (port 8081) which
 * exposes the searchJobs tool. This service calls it via the MCP client.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobSearchService {

    private final ResumeParserAiService resumeParser;
    private final ResumeRepository resumeRepository;
    private final JobRepository jobRepository;
    private final JobApplicationRepository applicationRepository;
    private final UserService userService;
    private final VectorStore vectorStore;
    private final ToolCallbackProvider atlasMcpToolCallbackProvider;

    @Transactional
    @AuditAction(action = "RESUME_UPLOAD", description = "Resume upload + AI parsing + embedding")
    @MeasurePerformance(warnThresholdMs = 15000)
    public Resume uploadResume(String fileName, String resumeText) {
        UUID userId = userService.requireCurrentUserId();
        ResumeParserAiService.ParsedResume parsed = resumeParser.parse(resumeText);
        if (parsed == null) {
            throw new IllegalStateException("Could not parse resume. Please try a different file.");
        }

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setFileName(fileName);
        resume.setFullName(parsed.getFullName() != null ? parsed.getFullName() : "Unknown");
        resume.setEmail(parsed.getEmail());
        resume.setPhone(parsed.getPhone());
        resume.setCurrentTitle(parsed.getCurrentTitle());
        resume.setYearsExperience(parsed.getYearsExperience());
        resume.setSkills(JsonUtil.toJson(parsed.getSkills() != null ? parsed.getSkills() : List.of()));
        resume.setExperience(JsonUtil.toJson(parsed.getExperience() != null ? parsed.getExperience() : List.of()));
        resume.setEducation(JsonUtil.toJson(parsed.getEducation() != null ? parsed.getEducation() : List.of()));
        resume.setLocation(parsed.getLocation());
        resume.setSummary(parsed.getSummary());
        resume.setPreferredRole(parsed.getPreferredRole());
        resume.setPreferredLocation(parsed.getPreferredLocation());
        resume.setSalaryExpectation(parsed.getSalaryExpectation());
        resume = resumeRepository.save(resume);

        // Embed the resume for semantic matching
        String resumeTextForEmbedding = buildResumeEmbeddingText(parsed);
        Document doc = Document.builder()
                .id("resume-" + resume.getId())
                .text(resumeTextForEmbedding)
                .metadata(Map.of("type", "resume", "resumeId", resume.getId().toString()))
                .build();
        try {
            vectorStore.delete(List.of("resume-" + resume.getId()));
        } catch (Exception ignored) {}
        vectorStore.add(List.of(doc));
        resume.setEmbeddingId("resume-" + resume.getId());
        resumeRepository.save(resume);

        log.info("Resume uploaded + embedded for user {}: {} ({})", userId, resume.getFullName(), resume.getCurrentTitle());
        return resume;
    }

    private String buildResumeEmbeddingText(ResumeParserAiService.ParsedResume p) {
        StringBuilder sb = new StringBuilder();
        sb.append("Title: ").append(p.getCurrentTitle()).append("\n");
        sb.append("Skills: ").append(String.join(", ", p.getSkills() != null ? p.getSkills() : List.of())).append("\n");
        sb.append("Experience: ").append(p.getYearsExperience()).append(" years\n");
        sb.append("Location: ").append(p.getLocation()).append("\n");
        sb.append("Summary: ").append(p.getSummary()).append("\n");
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public List<Resume> getResumes() {
        UUID userId = userService.requireCurrentUserId();
        return resumeRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    @AuditAction(action = "JOB_SEARCH", description = "Job search across multiple portals + match scoring")
    @MeasurePerformance(warnThresholdMs = 30000)
    @LogExecution(level = "INFO")
    public Map<String, Object> searchAndMatchJobs(UUID resumeId, int maxResults) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found: " + resumeId));

        List<String> skills = JsonUtil.fromJson(resume.getSkills());
        String keywords = resume.getPreferredRole() != null ? resume.getPreferredRole()
                : (skills.isEmpty() ? "Java developer" : skills.get(0) + " developer");
        String location = resume.getPreferredLocation() != null ? resume.getPreferredLocation() : "India";

        List<Map<String, Object>> rawJobs = callJobsMcpSearch(keywords, location, maxResults);

        UUID userId = userService.requireCurrentUserId();
        List<Map<String, Object>> matchedJobs = new ArrayList<>();

        for (Map<String, Object> raw : rawJobs) {
            String source = (String) raw.getOrDefault("source", "UNKNOWN");
            String sourceId = String.valueOf(raw.getOrDefault("sourceId", UUID.randomUUID()));

            Job job = jobRepository.findBySourceAndSourceId(source, sourceId)
                    .orElseGet(() -> persistJob(raw, source, sourceId));

            double score = computeMatchScore(resume, job);
            String reason = buildMatchReason(resume, job);

            JobApplication app = applicationRepository.findByUserIdAndJobId(userId, job.getId())
                    .orElseGet(() -> {
                        JobApplication a = new JobApplication();
                        a.setUserId(userId);
                        a.setJobId(job.getId());
                        a.setResumeId(resume.getId());
                        a.setStatus("NOT_APPLIED");
                        return a;
                    });
            app.setMatchScore(BigDecimal.valueOf(score * 100).setScale(2, RoundingMode.HALF_UP));
            app.setMatchReason(reason);
            applicationRepository.save(app);

            matchedJobs.add(Map.of(
                    "job", job,
                    "matchScore", score * 100,
                    "matchReason", reason,
                    "status", app.getStatus()
            ));
        }

        matchedJobs.sort((a, b) -> Double.compare((Double) b.get("matchScore"), (Double) a.get("matchScore")));

        return Map.of(
                "resume", resume,
                "keywords", keywords,
                "totalFound", rawJobs.size(),
                "matchedJobs", matchedJobs
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> callJobsMcpSearch(String keywords, String location, int max) {
        for (ToolCallback tc : atlasMcpToolCallbackProvider.getToolCallbacks()) {
            if (tc.getToolDefinition().name().equals("searchJobs")) {
                try {
                    String result = tc.call("""
                            {"keywords": "%s", "location": "%s", "maxResults": %d}
                            """.formatted(keywords, location, max));
                    return JsonUtil.getObjectMapper().readValue(result, List.class);
                } catch (Exception e) {
                    log.error("Jobs MCP searchJobs call failed: {}", e.getMessage());
                    return List.of();
                }
            }
        }
        log.warn("searchJobs tool not found in MCP callbacks — is the MCP server running on :8081?");
        return List.of();
    }

    private Job persistJob(Map<String, Object> raw, String source, String sourceId) {
        Job job = new Job();
        job.setSource(source);
        job.setSourceId(sourceId);
        job.setTitle((String) raw.getOrDefault("title", "Unknown"));
        job.setCompany((String) raw.get("company"));
        job.setLocation((String) raw.get("location"));
        job.setDescription((String) raw.getOrDefault("description", ""));
        job.setSalary((String) raw.get("salary"));
        job.setJobType((String) raw.getOrDefault("jobType", "Full-time"));
        job.setRemote(Boolean.TRUE.equals(raw.get("remote")));
        job.setApplyUrl((String) raw.getOrDefault("applyUrl", "#"));
        job.setFetchedAt(Instant.now());
        return jobRepository.save(job);
    }

    private double computeMatchScore(Resume resume, Job job) {
        List<String> skills = JsonUtil.fromJson(resume.getSkills());
        if (skills.isEmpty()) return 0.3;

        String jobText = (job.getTitle() + " " + job.getDescription()).toLowerCase();
        int matches = 0;
        for (String skill : skills) {
            if (jobText.contains(skill.toLowerCase())) matches++;
        }
        return Math.min(1.0, (double) matches / skills.size() + 0.1);
    }

    private String buildMatchReason(Resume resume, Job job) {
        List<String> skills = JsonUtil.fromJson(resume.getSkills());
        String jobText = (job.getTitle() + " " + job.getDescription()).toLowerCase();
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String skill : skills) {
            if (jobText.contains(skill.toLowerCase())) {
                matched.add("✓ " + skill);
            } else {
                missing.add("✗ " + skill + " (you have it)");
            }
        }
        return String.join("  ", matched) + (missing.isEmpty() ? "" : "  " + String.join("  ", missing.subList(0, Math.min(2, missing.size()))));
    }

    @Transactional(readOnly = true)
    public List<JobApplication> getTrackedJobs() {
        UUID userId = userService.requireCurrentUserId();
        return applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Transactional
    public JobApplication updateApplicationStatus(String applicationId, String status) {
        JobApplication app = applicationRepository.findById(UUID.fromString(applicationId))
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        app.setStatus(status.toUpperCase());
        if ("APPLIED".equals(app.getStatus()) && app.getAppliedAt() == null) {
            app.setAppliedAt(Instant.now());
        }
        return applicationRepository.save(app);
    }
}
