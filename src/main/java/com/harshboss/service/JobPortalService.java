package com.harshboss.service;

import com.harshboss.aspect.annotation.AuditAction;
import com.harshboss.aspect.annotation.LogExecution;
import com.harshboss.entity.JobPortalApplication;
import com.harshboss.entity.Resume;
import com.harshboss.repository.JobPortalApplicationRepository;
import com.harshboss.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * JobPortalService — the centralized job portals hub.
 *
 * <p>Provides:</p>
 * <ul>
 *   <li>Deep-link generation for all 7 portals (pre-filled with resume keywords)</li>
 *   <li>Application tracking per portal (status pipeline)</li>
 *   <li>Portal statistics (applications per portal, status breakdown)</li>
 *   <li>AI-powered portal recommendations based on the user's profile</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobPortalService {

    private final PortalConfig portalConfig;
    private final JobPortalApplicationRepository applicationRepository;
    private final ResumeRepository resumeRepository;
    private final UserService userService;

    // ═══════════════════════════════════════════════════════════════
    // 1. Portal configuration + deep links
    // ═══════════════════════════════════════════════════════════════

    /** Get all 7 portal configs with personalized search URLs. */
    public List<Map<String, Object>> getPortalsWithLinks() {
        UUID userId = userService.requireCurrentUserId();
        List<Resume> resumes = resumeRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // Extract keywords + location from the latest resume
        String keywords = "Java developer";
        String location = "India";
        Integer experience = null;

        if (!resumes.isEmpty()) {
            Resume resume = resumes.get(0);
            List<String> skills = com.harshboss.dto.JsonUtil.fromJson(resume.getSkills());
            if (resume.getPreferredRole() != null) {
                keywords = resume.getPreferredRole();
            } else if (!skills.isEmpty()) {
                keywords = skills.get(0) + " developer";
            }
            if (resume.getPreferredLocation() != null) {
                location = resume.getPreferredLocation();
            }
            if (resume.getYearsExperience() != null) {
                experience = resume.getYearsExperience();
            }
        }

        // Build the response with search URLs + application counts per portal
        List<Map<String, Object>> result = new ArrayList<>();
        for (PortalConfig.Portal portal : portalConfig.getAllPortals()) {
            String searchUrl = portalConfig.generateSearchUrl(
                    portal.getKey(), keywords, location, experience);

            long appCount = applicationRepository.countByUserIdAndPortal(userId, portal.getKey());

            result.add(Map.of(
                    "key", portal.getKey(),
                    "name", portal.getName(),
                    "baseUrl", portal.getBaseUrl(),
                    "loginUrl", portal.getLoginUrl(),
                    "profileUrl", portal.getProfileUrl(),
                    "searchUrl", searchUrl,
                    "bestFor", portal.getBestFor(),
                    "coverage", portal.getCoverage(),
                    "color", portal.getColor(),
                    "applicationCount", appCount
            ));
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. Application tracking
    // ═══════════════════════════════════════════════════════════════

    @Transactional
    @AuditAction(action = "JOB_PORTAL_APPLY", description = "Track a job application on a portal")
    @LogExecution(level = "INFO")
    public JobPortalApplication trackApplication(Map<String, String> body) {
        UUID userId = userService.requireCurrentUserId();

        JobPortalApplication app = new JobPortalApplication();
        app.setUserId(userId);
        app.setPortal(body.getOrDefault("portal", "NAUKRI").toUpperCase());
        app.setJobTitle(body.getOrDefault("jobTitle", "Unknown"));
        app.setCompany(body.get("company"));
        app.setJobUrl(body.get("jobUrl"));
        app.setLocation(body.get("location"));
        app.setSalary(body.get("salary"));
        app.setWorkMode(body.getOrDefault("workMode", "ONSITE").toUpperCase());
        app.setStatus(body.getOrDefault("status", "NOT_APPLIED").toUpperCase());
        app.setNotes(body.get("notes"));

        if ("APPLIED".equals(app.getStatus())) {
            app.setAppliedAt(Instant.now());
        }

        return applicationRepository.save(app);
    }

    @Transactional(readOnly = true)
    public List<JobPortalApplication> getTrackedApplications() {
        UUID userId = userService.requireCurrentUserId();
        return applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<JobPortalApplication> getApplicationsByPortal(String portal) {
        UUID userId = userService.requireCurrentUserId();
        return applicationRepository.findByUserIdAndPortalOrderByUpdatedAtDesc(userId, portal.toUpperCase());
    }

    @Transactional
    public JobPortalApplication updateStatus(String applicationId, String status) {
        JobPortalApplication app = applicationRepository.findById(UUID.fromString(applicationId))
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        app.setStatus(status.toUpperCase());
        if ("APPLIED".equals(app.getStatus()) && app.getAppliedAt() == null) {
            app.setAppliedAt(Instant.now());
        }
        return applicationRepository.save(app);
    }

    @Transactional
    public void deleteApplication(String applicationId) {
        applicationRepository.deleteById(UUID.fromString(applicationId));
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. Statistics
    // ═══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        UUID userId = userService.requireCurrentUserId();
        List<JobPortalApplication> all = applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId);

        Map<String, Long> byPortal = new LinkedHashMap<>();
        Map<String, Long> byStatus = new LinkedHashMap<>();

        for (PortalConfig.Portal portal : portalConfig.getAllPortals()) {
            byPortal.put(portal.getKey(), all.stream().filter(a -> a.getPortal().equals(portal.getKey())).count());
        }

        for (String status : List.of("NOT_APPLIED", "APPLIED", "INTERVIEW", "OFFER", "REJECTED")) {
            byStatus.put(status, all.stream().filter(a -> a.getStatus().equals(status)).count());
        }

        return Map.of(
                "total", (long) all.size(),
                "byPortal", byPortal,
                "byStatus", byStatus
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. Portal recommendations (rule-based, no LLM needed)
    // ═══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<Map<String, String>> getRecommendations() {
        UUID userId = userService.requireCurrentUserId();
        List<Resume> resumes = resumeRepository.findByUserIdOrderByCreatedAtDesc(userId);

        String role = "Java Developer";
        if (!resumes.isEmpty() && resumes.get(0).getPreferredRole() != null) {
            role = resumes.get(0).getPreferredRole().toLowerCase();
        }

        List<Map<String, String>> recommendations = new ArrayList<>();

        // Rule-based recommendations
        if (role.contains("senior") || role.contains("lead") || role.contains("architect")) {
            recommendations.add(Map.of(
                    "portal", "LINKEDIN",
                    "reason", "Senior roles get the best visibility on LinkedIn. Search for 'Senior Java Developer' and filter by 'Mid-Senior level'.",
                    "action", "Search LinkedIn Jobs"
            ));
            recommendations.add(Map.of(
                    "portal", "NAUKRI",
                    "reason", "Naukri has the highest volume of senior roles (5-8 years) at GCCs and Indian product firms.",
                    "action", "Search Naukri"
            ));
            recommendations.add(Map.of(
                    "portal", "TURING",
                    "reason", "Turing specializes in vetted senior engineers for international remote roles with USD compensation.",
                    "action", "Apply to Turing"
            ));
        }

        if (role.contains("full") || role.contains("full-stack") || role.contains("angular") || role.contains("react")) {
            recommendations.add(Map.of(
                    "portal", "CUTSHORT",
                    "reason", "Cutshort has active recruitment for Spring Boot + Angular full-stack roles at funded Indian startups.",
                    "action", "Search Cutshort"
            ));
            recommendations.add(Map.of(
                    "portal", "INSTAHYRE",
                    "reason", "Instahyre matches you with funded startups looking for full-stack developers. Quick turnaround times.",
                    "action", "Search Instahyre"
            ));
        }

        recommendations.add(Map.of(
                "portal", "WELLFOUND",
                "reason", "Wellfound is best for US/EU early-stage startups offering equity + USD salary for remote roles.",
                "action", "Browse Wellfound"
        ));

        recommendations.add(Map.of(
                "portal", "OTTA",
                "reason", "Otta curates high-quality tech companies from Europe and the US. Good for remote-friendly teams.",
                "action", "Browse Otta"
        ));

        return recommendations;
    }
}
