package com.harshboss.jobs.service;

import com.harshboss.aspect.annotation.AuditAction;
import com.harshboss.aspect.annotation.LogExecution;
import com.harshboss.aspect.annotation.MeasurePerformance;
import com.harshboss.dto.JsonUtil;
import com.harshboss.entity.JobPortalApplication;
import com.harshboss.entity.Resume;
import com.harshboss.jobs.client.GoogleCseClient;
import com.harshboss.jobs.client.JSearchClient;
import com.harshboss.jobs.client.SerpApiClient;
import com.harshboss.repository.JobPortalApplicationRepository;
import com.harshboss.repository.ResumeRepository;
import com.harshboss.service.PortalConfig;
import com.harshboss.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * JobAggregatorService — the centralized job aggregation engine.
 *
 * <p>Queries multiple legitimate job APIs (SerpAPI, Google CSE, JSearch),
 * deduplicates the results, and filters them through the {@link SkillMatchFilter}
 * so the user only sees jobs matching their tech stack.</p>
 *
 * <p>This is the "focused" job search — no noise, no irrelevant jobs.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobAggregatorService {

    private final SerpApiClient serpApiClient;
    private final GoogleCseClient googleCseClient;
    private final JSearchClient jSearchClient;
    private final SkillMatchFilter skillMatchFilter;
    private final ResumeRepository resumeRepository;
    private final JobPortalApplicationRepository applicationRepository;
    private final UserService userService;
    private final PortalConfig portalConfig;

    /**
     * Search for skill-matched jobs across all configured sources.
     *
     * @param maxResults max results per source
     * @return aggregated + deduplicated + skill-filtered jobs, sorted by match score
     */
    @Transactional(readOnly = true)
    @MeasurePerformance(warnThresholdMs = 20000)
    @LogExecution(level = "INFO")
    @AuditAction(action = "SKILL_MATCHED_JOB_SEARCH", description = "Aggregated job search with skill filtering")
    public Map<String, Object> searchSkillMatchedJobs(int maxResults) {
        UUID userId = userService.requireCurrentUserId();
        List<Resume> resumes = resumeRepository.findByUserIdOrderByCreatedAtDesc(userId);

        if (resumes.isEmpty()) {
            return Map.of("error", "No resume uploaded. Please upload your resume in Profile first.");
        }

        Resume resume = resumes.get(0);
        List<String> skills = JsonUtil.fromJson(resume.getSkills());

        // Build search keywords from the user's preferred role + top skills
        String keywords = resume.getPreferredRole() != null ? resume.getPreferredRole()
                : (skills.isEmpty() ? "Java developer" : String.join(" ", skills.subList(0, Math.min(3, skills.size()))));
        String location = resume.getPreferredLocation() != null ? resume.getPreferredLocation() : "India";

        log.info("Aggregating skill-matched jobs: keywords='{}', location='{}', skills={}",
                keywords, location, skills);

        // 1. Query all sources
        List<Map<String, Object>> allJobs = new ArrayList<>();
        allJobs.addAll(serpApiClient.searchJobs(keywords, location));
        allJobs.addAll(googleCseClient.searchJobs(keywords, location));
        allJobs.addAll(jSearchClient.searchJobs(keywords, location));

        // 2. Deduplicate by title + company
        List<Map<String, Object>> deduped = deduplicate(allJobs);

        // 3. Filter by skills (the key feature — only show jobs matching the user's tech stack)
        List<Map<String, Object>> matched = skillMatchFilter.filter(deduped, resume);

        // 4. Build portal deep links for manual search on the 7 portals
        List<Map<String, Object>> portalLinks = new ArrayList<>();
        for (PortalConfig.Portal portal : portalConfig.getAllPortals()) {
            String searchUrl = portalConfig.generateSearchUrl(portal.getKey(), keywords, location, resume.getYearsExperience());
            long appCount = applicationRepository.countByUserIdAndPortal(userId, portal.getKey());
            portalLinks.add(Map.of(
                    "key", portal.getKey(),
                    "name", portal.getName(),
                    "searchUrl", searchUrl,
                    "applicationCount", appCount,
                    "bestFor", portal.getBestFor(),
                    "coverage", portal.getCoverage()
            ));
        }

        // 5. Get current tracked applications count
        List<JobPortalApplication> tracked = applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId);

        return Map.of(
                "resume", Map.of(
                        "fullName", resume.getFullName(),
                        "currentTitle", resume.getCurrentTitle() != null ? resume.getCurrentTitle() : "",
                        "skills", skills,
                        "preferredRole", resume.getPreferredRole() != null ? resume.getPreferredRole() : "",
                        "preferredLocation", location,
                        "yearsExperience", resume.getYearsExperience() != null ? resume.getYearsExperience() : 0
                ),
                "matchedJobs", matched,
                "totalFound", allJobs.size(),
                "totalAfterDedup", deduped.size(),
                "totalAfterSkillFilter", matched.size(),
                "portalLinks", portalLinks,
                "trackedCount", tracked.size(),
                "searchKeywords", keywords
        );
    }

    /** Deduplicate jobs by title + company (case-insensitive). */
    private List<Map<String, Object>> deduplicate(List<Map<String, Object>> jobs) {
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> job : jobs) {
            String title = String.valueOf(job.getOrDefault("title", "")).toLowerCase().trim();
            String company = String.valueOf(job.getOrDefault("company", "")).toLowerCase().trim();
            String key = title + "|" + company;

            if (!seen.contains(key)) {
                seen.add(key);
                result.add(job);
            }
        }

        log.info("Deduplication: {} jobs → {} unique", jobs.size(), result.size());
        return result;
    }
}
