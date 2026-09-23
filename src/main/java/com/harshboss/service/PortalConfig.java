package com.harshboss.service;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Hardcoded configuration for the 7 supported job portals.
 *
 * <p>URL patterns are based on the latest (2025) portal documentation.
 * Each portal has a search URL generator that takes keywords + location
 * and produces a pre-filled search results page URL.</p>
 *
 * <p>Since none of these portals have open public APIs, we use deep-linking:
 * the user clicks "Search →" and we open the portal's search page with their
 * resume keywords already filled in. They apply on the portal, then track
 * the application status back in Harsh-Boss.</p>
 */
@Component
public class PortalConfig {

    public static final List<Portal> PORTALS = List.of(
        new Portal("NAUKRI", "Naukri", "https://www.naukri.com",
            "https://www.naukri.com/jobs-in-india?k={keywords}&l={location}&experience={experience}",
            "https://www.naukri.com/nlogin/login",
            "https://my.naukri.com/Profile/view",
            "Highest Indian job volume. Best for mid-to-senior roles (5-8 years) at GCCs, product firms, and system integrators.",
            "🇮🇳 India", "emerald"),

        new Portal("LINKEDIN", "LinkedIn Jobs", "https://www.linkedin.com/jobs",
            "https://www.linkedin.com/jobs/search/?keywords={keywords}&location={location}&f_E={experienceLevel}&f_WT=2&sortBy=DD",
            "https://www.linkedin.com/login",
            "https://www.linkedin.com/in/me/",
            "Best overall. Strongest inventory for Full-Stack/Backend roles in Indian tech hubs + remote multi-national.",
            "🌍 Global", "blue"),

        new Portal("WELLFOUND", "Wellfound", "https://wellfound.com/jobs",
            "https://wellfound.com/role/l/{role-slug}/{location-slug}",
            "https://wellfound.com/login",
            "https://wellfound.com/profile",
            "Best for US/EU early-stage startups. USD-denominated compensation for international remote roles.",
            "🌍 Global Startups", "rose"),

        new Portal("INSTAHYRE", "Instahyre", "https://www.instahyre.com",
            "https://www.instahyre.com/{keywords}-jobs/",
            "https://www.instahyre.com/candidate/login/",
            "https://www.instahyre.com/candidate/profile/",
            "Best for funded Indian startups & mid-sized product companies. Quick turnaround times.",
            "🇮🇳 India Startups", "amber"),

        new Portal("OTTA", "Otta", "https://otta.com",
            "https://www.welcometothejungle.com/en/jobs?query={keywords}&aroundQuery={location}",
            "https://app.otta.com/login",
            "https://app.otta.com/profile",
            "Highly focused on European and US tech brands. Remote-friendly European teams.",
            "🌍 EU/US Tech", "violet"),

        new Portal("TURING", "Turing", "https://www.turing.com",
            "https://www.turing.com/jobs?skill={skill}",
            "https://talent.turing.com/login",
            "https://talent.turing.com/profile",
            "Best for international remote (USD pay). Vets senior backend engineers with system architecture skills.",
            "🌍 Remote US", "teal"),

        new Portal("CUTSHORT", "Cutshort", "https://cutshort.io",
            "https://cutshort.io/jobs/{keywords}-jobs",
            "https://cutshort.io/candidate/login",
            "https://cutshort.io/candidate/profile",
            "Best for funded Indian startups. Active recruitment for Spring Boot + Angular stack.",
            "🇮🇳 India Startups", "slate")
    );

    /**
     * Generate a personalized search URL for a given portal.
     *
     * @param portalKey   e.g., "NAUKRI"
     * @param keywords    e.g., "Java Spring Boot"
     * @param location    e.g., "Bangalore" or "India" or "remote"
     * @param experience  years of experience (e.g., 6)
     * @return the full URL to open in the browser
     */
    public String generateSearchUrl(String portalKey, String keywords, String location, Integer experience) {
        Portal portal = PORTALS.stream()
                .filter(p -> p.getKey().equalsIgnoreCase(portalKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown portal: " + portalKey));

        String url = portal.getSearchUrlPattern();
        String encodedKeywords = encode(keywords);
        String encodedLocation = location != null ? encode(location) : "";

        // Replace placeholders
        url = url.replace("{keywords}", encodedKeywords);
        url = url.replace("{location}", encodedLocation);
        url = url.replace("{role-slug}", slugify(keywords));
        url = url.replace("{location-slug}", slugify(location != null ? location : "india"));
        url = url.replace("{skill}", slugify(keywords).split("-")[0]); // first keyword for Turing

        // Experience level mapping
        if (experience != null) {
            url = url.replace("{experience}", String.valueOf(experience));
            // LinkedIn experience levels: 2=Entry, 3=Associate, 4=Mid-Senior, 5=Director
            String linkedinLevel = experience <= 2 ? "2" : experience <= 4 ? "3" : "4";
            url = url.replace("{experienceLevel}", linkedinLevel);
        } else {
            url = url.replace("{experience}", "");
            url = url.replace("{experienceLevel}", "4"); // default Mid-Senior
        }

        return url;
    }

    public List<Portal> getAllPortals() {
        return PORTALS;
    }

    public Portal getPortal(String key) {
        return PORTALS.stream()
                .filter(p -> p.getKey().equalsIgnoreCase(key))
                .findFirst()
                .orElse(null);
    }

    private String encode(String s) {
        return s != null ? s.replace(" ", "%20") : "";
    }

    private String slugify(String s) {
        return s != null ? s.toLowerCase().trim().replaceAll("[^a-z0-9]+", "-") : "";
    }

    @Data
    public static class Portal {
        private final String key;           // NAUKRI
        private final String name;          // Naukri
        private final String baseUrl;       // https://www.naukri.com
        private final String searchUrlPattern; // template with {keywords}, {location}, etc.
        private final String loginUrl;
        private final String profileUrl;
        private final String bestFor;       // recommendation text
        private final String coverage;     // e.g., "🇮🇳 India"
        private final String color;        // tailwind color name

        public Portal(String key, String name, String baseUrl, String searchUrlPattern,
                      String loginUrl, String profileUrl, String bestFor, String coverage, String color) {
            this.key = key;
            this.name = name;
            this.baseUrl = baseUrl;
            this.searchUrlPattern = searchUrlPattern;
            this.loginUrl = loginUrl;
            this.profileUrl = profileUrl;
            this.bestFor = bestFor;
            this.coverage = coverage;
            this.color = color;
        }
    }
}
