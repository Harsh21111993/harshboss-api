package com.harshboss.jobs.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * Google Custom Search Engine (CSE) client — searches for jobs specifically
 * on the 7 target portals using the site: operator.
 *
 * <p>API: https://www.googleapis.com/customsearch/v1?key={key}&cx={cx}&q={query}</p>
 *
 * <p>The CSE must be configured to search the 7 portal domains.</p>
 */
@Slf4j
@Component
public class GoogleCseClient {

    @Value("${harshboss.job-aggregation.google-cse.api-key:}")
    private String apiKey;

    @Value("${harshboss.job-aggregation.google-cse.search-engine-id:}")
    private String searchEngineId;

    private final WebClient client = WebClient.builder().baseUrl("https://www.googleapis.com").build();

    /** The 7 portal domains to search */
    private static final String PORTAL_DOMAINS = "site:naukri.com OR site:linkedin.com/jobs OR site:wellfound.com/jobs OR site:instahyre.com OR site:otta.com OR site:turing.com OR site:cutshort.io";

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchJobs(String keywords, String location) {
        if (apiKey == null || apiKey.isBlank() || searchEngineId == null || searchEngineId.isBlank()) {
            log.debug("Google CSE: no API key or search engine ID configured, skipping");
            return List.of();
        }

        try {
            String query = keywords + " jobs" + (location != null ? " " + location : "") + " (" + PORTAL_DOMAINS + ")";

            Map<String, Object> resp = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/customsearch/v1")
                            .queryParam("key", apiKey)
                            .queryParam("cx", searchEngineId)
                            .queryParam("q", query)
                            .queryParam("gl", "in")
                            .queryParam("num", 10)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (resp == null) return List.of();

            List<Map<String, Object>> items = (List<Map<String, Object>>) resp.get("items");
            if (items == null) return List.of();

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> item : items) {
                String url = (String) item.getOrDefault("link", "");
                Map<String, Object> normalized = new HashMap<>();
                normalized.put("source", detectPortalFromUrl(url));
                normalized.put("title", item.getOrDefault("title", "Unknown"));
                normalized.put("company", "Unknown");
                normalized.put("location", location != null ? location : "Unknown");
                normalized.put("description", item.getOrDefault("snippet", ""));
                normalized.put("applyUrl", url);
                normalized.put("postedAt", "");
                normalized.put("remote", url.toLowerCase().contains("remote"));
                result.add(normalized);
            }

            log.info("Google CSE: found {} results for '{}'", result.size(), keywords);
            return result;
        } catch (Exception e) {
            log.error("Google CSE search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String detectPortalFromUrl(String url) {
        String lower = url.toLowerCase();
        if (lower.contains("naukri.com")) return "NAUKRI";
        if (lower.contains("linkedin.com")) return "LINKEDIN";
        if (lower.contains("wellfound.com")) return "WELLFOUND";
        if (lower.contains("instahyre.com")) return "INSTAHYRE";
        if (lower.contains("otta.com") || lower.contains("welcometothejungle.com")) return "OTTA";
        if (lower.contains("turing.com")) return "TURING";
        if (lower.contains("cutshort.io")) return "CUTSHORT";
        return "OTHER";
    }
}
