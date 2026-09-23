package com.harshboss.ai;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Parses a resume (text extracted from PDF) into a structured profile using
 * Gemini's structured output (.entity()).
 */
@Slf4j
@Service
public class ResumeParserAiService {

    private final ChatClient chatClient;

    private static final String SYSTEM = """
            You are a resume parser. Extract the candidate's profile from the resume text.
            Respond with valid JSON only matching the ParsedResume schema.

            Rules:
            - Extract skills as a flat array of strings (e.g. ["Java", "Spring Boot", "Angular"]).
            - For experience, extract each role as { role, company, duration, summary }.
            - For education, extract each degree as { degree, institution, year }.
            - Generate a concise professional summary (2-3 sentences) highlighting the candidate's strengths.
            - Infer preferred_role from the most recent job title.
            - Infer preferred_location from the address or location mentioned. Default to "India" if unclear.
            - Estimate salary_expectation from the experience level + market norms (e.g. "₹15-25 LPA").
            - years_experience: count total years across all roles.
            """;

    public ResumeParserAiService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public ParsedResume parse(String resumeText) {
        try {
            ParsedResume result = chatClient.prompt()
                    .system(SYSTEM)
                    .user(resumeText)
                    .call()
                    .entity(ParsedResume.class);

            if (result == null) {
                log.warn("Resume parsing returned null");
                return null;
            }
            log.info("Resume parsed: {} — {} skills, {} years exp",
                    result.fullName, result.skills != null ? result.skills.size() : 0, result.yearsExperience);
            return result;
        } catch (Exception e) {
            log.error("Resume parsing failed: {}", e.getMessage(), e);
            return null;
        }
    }

    @Data
    public static class ParsedResume {
        public String fullName;
        public String email;
        public String phone;
        public String currentTitle;
        public Integer yearsExperience;
        public List<String> skills;
        public List<ExperienceEntry> experience;
        public List<EducationEntry> education;
        public String location;
        public String summary;
        public String preferredRole;
        public String preferredLocation;
        public String salaryExpectation;
    }

    @Data
    public static class ExperienceEntry {
        public String role;
        public String company;
        public String duration;
        public String summary;
    }

    @Data
    public static class EducationEntry {
        public String degree;
        public String institution;
        public String year;
    }
}
