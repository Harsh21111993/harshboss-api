package com.harshboss.jobs.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * SerpAPI Google Jobs client — aggregates jobs from Naukri, LinkedIn, and other
 * portals via Google's Jobs search engine.
 *
 * <p>API: https://serpapi.com/search.json?engine=google_jobs&q={keywords}&location={location}</p>
 *
 * <p>Free tier: 100 searches/month.</p>
 */
@Slf4j
@Component
public class SerpApiClient {

    @Value("${harshboss.job-aggregation.serpapi.api-key:}")
    private String apiKey;

    private final WebClient client = WebClient.builder().baseUrl("https://serpapi.com").build();

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchJobs(String keywords, String location) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("SerpAPI: no API key configured, skipping");
            return List.of();
        }

        try {
            Map<String, Object> resp = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search.json")
                            .queryParam("engine", "google_jobs")
                            .queryParam("q", keywords)
                            .queryParamIfPresent("location", location != null ? Optional.of(location) : Optional.empty())
                            .queryParam("hl", "en")
                            .queryParam("gl", "in")
                            .queryParam("api_key", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) return List.of();

            List<Map<String, Object>> jobsResults = (List<Map<String, Object>>) resp.get("jobs_results");
            if (jobsResults == null) return List.of();

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> job : jobsResults) {
                Map<String, Object> normalized = new HashMap<>();
                normalized.put("source", detectPortal(job));
                normalized.put("title", job.getOrDefault("title", "Unknown"));
                normalized.put("company", job.getOrDefault("company_name", "Unknown"));
                normalized.put("location", job.getOrDefault("location", "Unknown"));
                normalized.put("description", job.getOrDefault("description", ""));
                normalized.put("applyUrl", job.getOrDefault("apply_link", job.getOrDefault("share_link", "#")));
                normalized.put("postedAt", job.getOrDefault("detected_time", ""));
                // Detect portal from extensions or source
                normalized.put("remote", job.getOrDefault("via", "").toString().toLowerCase().contains("remote"));
                result.add(normalized);
            }

            log.info("SerpAPI: found {} jobs for '{}'", result.size(), keywords);
            return result;
        } catch (Exception e) {
            log.error("SerpAPI search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Detect which portal the job came from based on the 'via' field. */
    private String detectPortal(Map<String, Object> job) {
        String via = job.getOrDefault("via", "").toString().toLowerCase();
        if (via.contains("naukri")) return "NAUKRI";
        if (via.contains("linkedin")) return "LINKEDIN";
        if (via.contains("wellfound") || via.contains("angellist")) return "WELLFOUND";
        if (via.contains("instahyre")) return "INSTAHYRE";
        if (via.contains("otta") || via.contains("welcometothejungle")) return "OTTA";
        if (via.contains("turing")) return "TURING";
        if (via.contains("cutshort")) return "CUTSHORT";
        return "GOOGLE_JOBS";
    }
}
