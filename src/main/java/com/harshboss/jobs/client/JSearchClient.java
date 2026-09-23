package com.harshboss.jobs.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * JSearch (RapidAPI) client — aggregates jobs from multiple sources.
 *
 * <p>API: https://jsearch.p.rapidapi.com/search?query={keywords}</p>
 *
 * <p>Free tier: ~500 requests/month.</p>
 */
@Slf4j
@Component
public class JSearchClient {

    @Value("${harshboss.job-aggregation.rapidapi.key:}")
    private String apiKey;

    @Value("${harshboss.job-aggregation.rapidapi.host:jsearch.p.rapidapi.com}")
    private String apiHost;

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchJobs(String keywords, String location) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("JSearch: no RapidAPI key configured, skipping");
            return List.of();
        }

        try {
            String query = keywords + (location != null ? " in " + location : "");

            Map<String, Object> resp = WebClient.builder().baseUrl("https://" + apiHost).build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("query", query)
                            .queryParam("page", 1)
                            .queryParam("num_pages", 1)
                            .build())
                    .header("X-RapidAPI-Key", apiKey)
                    .header("X-RapidAPI-Host", apiHost)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) return List.of();

            List<Map<String, Object>> data = (List<Map<String, Object>>) resp.get("data");
            if (data == null) return List.of();

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> job : data) {
                Map<String, Object> normalized = new HashMap<>();
                normalized.put("source", "JSEARCH");
                normalized.put("title", job.getOrDefault("job_title", "Unknown"));
                normalized.put("company", job.getOrDefault("employer_name", "Unknown"));
                normalized.put("location", job.getOrDefault("job_city", job.getOrDefault("job_country", "Unknown")));
                normalized.put("description", job.getOrDefault("job_description", ""));
                normalized.put("applyUrl", job.getOrDefault("job_apply_link", job.getOrDefault("employer_website", "#")));
                normalized.put("postedAt", job.getOrDefault("job_posted_at_datetime_utc", ""));
                normalized.put("remote", "remote".equalsIgnoreCase(String.valueOf(job.getOrDefault("job_work_from_home", "no"))));
                // Salary
                Object minSal = job.get("job_min_salary");
                Object maxSal = job.get("job_max_salary");
                if (minSal != null && maxSal != null) {
                    normalized.put("salary", minSal + " - " + maxSal + " " + job.getOrDefault("job_salary_currency", ""));
                } else {
                    normalized.put("salary", "");
                }
                result.add(normalized);
            }

            log.info("JSearch: found {} jobs for '{}'", result.size(), keywords);
            return result;
        } catch (Exception e) {
            log.error("JSearch search failed: {}", e.getMessage());
            return List.of();
        }
    }
}
