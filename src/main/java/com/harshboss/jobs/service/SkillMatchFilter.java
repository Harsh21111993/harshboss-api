package com.harshboss.jobs.service;

import com.harshboss.dto.JsonUtil;
import com.harshboss.entity.Resume;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * SkillMatchFilter — the intelligence layer that filters job listings
 * to ONLY show jobs matching the user's tech stack.
 *
 * <p>This is the key feature: instead of showing every "Java developer" job,
 * it checks each job description against the user's actual skills and only
 * keeps the ones that match.</p>
 *
 * <p>For example, if the user's skills are [Java, Spring Boot, Angular, PostgreSQL,
 * Microservices, Docker, Kubernetes], a job that mentions PHP or .NET will be
 * filtered out — even if it appeared in the search results.</p>
 */
@Slf4j
@Service
public class SkillMatchFilter {

    /** Minimum number of skills that must match (as a fraction of total skills). */
    private static final double MIN_MATCH_RATIO = 0.3; // at least 30% of skills must match

    /**
     * Filter a list of jobs, keeping only those that match the user's skills.
     *
     * @param jobs   the raw job listings (each is a Map with "title", "description", etc.)
     * @param resume the user's resume (contains skills)
     * @return only the jobs that match, sorted by match score (highest first)
     */
    public List<Map<String, Object>> filter(List<Map<String, Object>> jobs, Resume resume) {
        if (jobs == null || jobs.isEmpty()) return List.of();
        if (resume == null || resume.getSkills() == null) return jobs;

        List<String> userSkills = JsonUtil.fromJson(resume.getSkills());
        if (userSkills.isEmpty()) {
            log.debug("SkillMatchFilter: no skills in resume — returning all jobs");
            return jobs;
        }

        // Normalize user skills (lowercase, trim)
        List<String> normalizedSkills = userSkills.stream()
                .map(s -> s.toLowerCase().trim())
                .filter(s -> !s.isBlank())
                .toList();

        log.info("SkillMatchFilter: filtering {} jobs against {} skills: {}",
                jobs.size(), normalizedSkills.size(), normalizedSkills);

        List<Map<String, Object>> matched = new ArrayList<>();
        int filteredOut = 0;

        for (Map<String, Object> job : jobs) {
            String jobText = ((String) job.getOrDefault("title", "")) + " "
                    + ((String) job.getOrDefault("description", "")) + " "
                    + ((String) job.getOrDefault("company", ""));
            String lowerJobText = jobText.toLowerCase();

            // Count how many of the user's skills appear in the job description
            List<String> matchedSkills = new ArrayList<>();
            List<String> missingSkills = new ArrayList<>();

            for (String skill : normalizedSkills) {
                if (lowerJobText.contains(skill)) {
                    matchedSkills.add(skill);
                } else {
                    missingSkills.add(skill);
                }
            }

            double matchRatio = (double) matchedSkills.size() / normalizedSkills.size();

            // Also check for negative signals (technologies the user doesn't know)
            List<String> negativeSignals = detectNegativeSignals(lowerJobText, normalizedSkills);
            if (!negativeSignals.isEmpty()) {
                // If the job is primarily about a technology the user doesn't have, skip it
                if (negativeSignals.size() >= 2 && matchedSkills.size() <= 1) {
                    log.debug("SkillMatchFilter: REJECTED '{}' — wrong stack: {}",
                            job.get("title"), negativeSignals);
                    filteredOut++;
                    continue;
                }
            }

            // Check if the job meets the minimum match ratio
            if (matchRatio >= MIN_MATCH_RATIO) {
                Map<String, Object> enrichedJob = new HashMap<>(job);
                enrichedJob.put("matchScore", Math.round(matchRatio * 100));
                enrichedJob.put("matchedSkills", matchedSkills);
                enrichedJob.put("missingSkills", missingSkills.subList(0, Math.min(3, missingSkills.size())));
                enrichedJob.put("matchReason", buildMatchReason(matchedSkills, missingSkills));
                matched.add(enrichedJob);
            } else {
                filteredOut++;
                log.debug("SkillMatchFilter: filtered out '{}' (matched {}/{})",
                        job.get("title"), matchedSkills.size(), normalizedSkills.size());
            }
        }

        // Sort by match score descending
        matched.sort((a, b) -> Integer.compare(
                (Integer) b.getOrDefault("matchScore", 0),
                (Integer) a.getOrDefault("matchScore", 0)));

        log.info("SkillMatchFilter: {} jobs matched, {} filtered out", matched.size(), filteredOut);
        return matched;
    }

    /** Detect technologies in the job that are NOT in the user's skill set. */
    private List<String> detectNegativeSignals(String jobText, List<String> userSkills) {
        List<String> negatives = new ArrayList<>();
        // Common tech stacks that are alternatives to Java/Spring/Angular
        Map<String, List<String>> alternatives = Map.of(
                "java", List.of("c#", ".net", "python django", "ruby on rails", "php laravel"),
                "spring boot", List.of("asp.net", "express.js", "django", "flask"),
                "angular", List.of("react", "vue.js", "svelte"),
                "postgresql", List.of("mongodb", "mysql only", "sql server only")
        );

        for (String userSkill : userSkills) {
            List<String> alts = alternatives.get(userSkill);
            if (alts != null) {
                for (String alt : alts) {
                    if (jobText.contains(alt) && !userSkills.contains(alt)) {
                        negatives.add(alt);
                    }
                }
            }
        }

        return negatives;
    }

    private String buildMatchReason(List<String> matched, List<String> missing) {
        StringBuilder sb = new StringBuilder();
        for (String s : matched.subList(0, Math.min(3, matched.size()))) {
            sb.append("✓ ").append(s).append("  ");
        }
        if (!missing.isEmpty()) {
            sb.append("  ");
            for (String s : missing.subList(0, Math.min(2, missing.size()))) {
                sb.append("✗ ").append(s).append("  ");
            }
        }
        return sb.toString().trim();
    }
}
