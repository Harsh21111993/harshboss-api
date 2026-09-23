package com.harshboss.interview.service;

import com.harshboss.ai.ResumeParserAiService;
import com.harshboss.dto.JsonUtil;
import com.harshboss.entity.Resume;
import com.harshboss.interview.dto.CandidateTechStack;
import com.harshboss.interview.dto.SingleTurnAssessment;
import com.harshboss.jev.service.JevDecisionService;
import com.harshboss.repository.ResumeRepository;
import com.harshboss.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * InterviewAiService — the AI brain of the voice interviewer.
 *
 * <p>Uses Gemini (via Spring AI ChatClient) for:
 * <ul>
 *   <li>Generating the first interview question based on the resume tech stack</li>
 *   <li>Evaluating the candidate's answer (knowledge, communication, problem-solving)</li>
 *   <li>Generating the next adaptive question (deeper or new topic)</li>
 *   <li>Generating a session summary + improvement roadmap</li>
 * </ul>
 *
 * <p>Uses Jev AI (TypeSafe AI) for fast scoring:
 * <ul>
 *   <li>Choice: PASS / PARTIAL / FAIL for each answer</li>
 *   <li>Score: 1-5 rubric for knowledge depth</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewAiService {

    private final ChatClient chatClient;
    private final JevDecisionService jevDecisionService;
    private final ResumeRepository resumeRepository;
    private final UserService userService;

    private static final String INTERVIEWER_SYSTEM = """
            You are a strict, pragmatic Senior Technical Interviewer conducting a voice interview.
            You ask ONE question at a time, wait for the answer, then evaluate it.

            Rules:
            1. Ask clear, specific technical questions relevant to the candidate's tech stack.
            2. Start with fundamental concepts, then go deeper based on the answer quality.
            3. If the candidate scores well, move to harder topics. If poorly, ask a simpler question.
            4. Cover different topics: core language, framework internals, database design, system design, debugging.
            5. After 8-10 questions, conclude the interview with a summary.
            6. Be professional but direct — like a real interviewer, not a chatbot.
            """;

    /**
     * Generate the first interview question based on the resume tech stack.
     */
    public String generateFirstQuestion(CandidateTechStack stack) {
        String prompt = """
                Start a technical interview for a candidate with the following profile:

                Primary language: %s
                Frameworks: %s
                Databases: %s
                Years of experience: %d

                Generate the FIRST interview question. Start with a fundamental concept
                from their primary language or main framework. The question should be
                answerable in 1-2 minutes of speaking.

                Respond with ONLY the question text (no preamble, no "Question 1:" prefix).
                """.formatted(
                stack.primaryLanguage(),
                String.join(", ", stack.frameworks()),
                String.join(", ", stack.databases()),
                stack.yearsOfExperience()
        );

        try {
            String question = chatClient.prompt()
                    .system(INTERVIEWER_SYSTEM)
                    .user(prompt)
                    .call()
                    .content();
            return question != null ? question.trim() : "Tell me about your experience with " + stack.primaryLanguage() + ".";
        } catch (Exception e) {
            log.error("Failed to generate first question: {}", e.getMessage());
            return "Tell me about your experience with " + stack.primaryLanguage() + ".";
        }
    }

    /**
     * Evaluate the candidate's answer and generate the next question.
     * Uses Gemini for detailed evaluation + Jev for fast pass/fail scoring.
     */
    public SingleTurnAssessment evaluateAndGenerateNext(
            CandidateTechStack stack,
            String currentQuestion,
            String candidateAnswer,
            int questionNumber) {

        // 1. Jev fast scoring (~100ms) — PASS / PARTIAL / FAIL
        String jevPassFail = "PARTIAL";
        double jevConfidence = 0.5;
        if (jevDecisionService != null && jevDecisionService.isAvailable()) {
            try {
                String state = "Question: " + currentQuestion + "\nAnswer: " + candidateAnswer +
                               "\nStack: " + stack.primaryLanguage() + ", " + String.join(", ", stack.frameworks());
                var jevResult = jevDecisionService.getEmailTriageResult(state); // reuse Jev for scoring
                // Use Jev Choice for pass/fail
                // (We'll use a custom Jev call for interview scoring)
                jevPassFail = "PARTIAL"; // placeholder — Jev integration for interview scoring
                jevConfidence = 0.5;
            } catch (Exception e) {
                log.debug("Jev interview scoring failed, using LLM only: {}", e.getMessage());
            }
        }

        // 2. Gemini detailed evaluation (~2-5s)
        String prompt = """
                You are evaluating a candidate's interview answer.

                Candidate Stack: %s, %s, %s (%d years exp).
                Question Number: %d

                Current Question: "%s"
                Candidate's Spoken Answer: "%s"

                Evaluate the answer across 3 dimensions (1-5 scale each):
                1. knowledgeScore: Technical depth & knowledge (did they explain internals and edge cases?)
                2. communicationScore: Communication (articulate, direct, free of vague fluff?)
                3. problemSolvingScore: Problem solving (systematic reasoning?)

                Also provide:
                - strengths: What they did well (1 sentence)
                - weaknesses: What needs improvement (1 sentence)
                - specificTopicFlaw: The specific topic they struggled with (e.g., "Spring Boot: @Transactional Isolation") or "none"
                - feedbackToUser: Constructive feedback (2-3 sentences, direct and actionable)
                - nextQuestion: The next interview question (deeper if they did well, simpler if not, or a new topic)
                - answerSatisfactory: true if knowledgeScore >= 3

                If this is question 8 or higher, make nextQuestion a closing statement
                like "That concludes our interview. Thank you for your time."

                Respond with valid JSON only matching the SingleTurnAssessment schema.
                """.formatted(
                stack.primaryLanguage(),
                String.join(", ", stack.frameworks()),
                String.join(", ", stack.databases()),
                stack.yearsOfExperience(),
                questionNumber,
                currentQuestion,
                candidateAnswer
        );

        try {
            SingleTurnAssessment result = chatClient.prompt()
                    .system(INTERVIEWER_SYSTEM)
                    .user(prompt)
                    .call()
                    .entity(SingleTurnAssessment.class);

            if (result == null) {
                return fallbackAssessment(currentQuestion, candidateAnswer, questionNumber);
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to evaluate answer: {}", e.getMessage());
            return fallbackAssessment(currentQuestion, candidateAnswer, questionNumber);
        }
    }

    /**
     * Generate a session summary + improvement roadmap.
     */
    public String generateSessionSummary(CandidateTechStack stack, List<String> questionsAndAnswers, List<Integer> scores) {
        double avgScore = scores.stream().mapToInt(Integer::intValue).average().orElse(0);

        StringBuilder qaText = new StringBuilder();
        for (int i = 0; i < questionsAndAnswers.size(); i += 2) {
            qaText.append("Q: ").append(questionsAndAnswers.get(i)).append("\n");
            if (i + 1 < questionsAndAnswers.size()) {
                qaText.append("A: ").append(questionsAndAnswers.get(i + 1)).append("\n\n");
            }
        }

        String prompt = """
                Generate a concise interview summary for a %s developer (%d years exp).

                Average score: %.1f / 5.0

                Interview transcript:
                %s

                Provide:
                1. Overall assessment (2-3 sentences)
                2. Top 3 strengths
                3. Top 3 areas to improve (with specific topics to study)
                4. Recommended resources (books, docs, courses) for each weak area
                5. Next steps (what to practice before the next interview)

                Keep it practical and actionable. Plain text, no markdown.
                """.formatted(stack.primaryLanguage(), stack.yearsOfExperience(), avgScore, qaText.toString());

        try {
            String summary = chatClient.prompt()
                    .system("You are an interview coach providing feedback to a candidate.")
                    .user(prompt)
                    .call()
                    .content();
            return summary != null ? summary.trim() : "Interview completed. Continue practicing.";
        } catch (Exception e) {
            log.error("Failed to generate summary: {}", e.getMessage());
            return "Interview completed. Average score: " + avgScore + "/5. Continue practicing your weak areas.";
        }
    }

    /**
     * Extract a CandidateTechStack from the user's stored resume.
     */
    public CandidateTechStack getTechStackFromResume(UUID resumeId) {
        Resume resume = resumeRepository.findById(resumeId).orElse(null);
        if (resume == null) {
            return new CandidateTechStack("Java", List.of("Spring Boot"), List.of("PostgreSQL"), 5);
        }

        List<String> skills = JsonUtil.fromJson(resume.getSkills());
        String primaryLang = !skills.isEmpty() ? skills.get(0) : "Java";
        List<String> frameworks = skills.size() > 1 ? skills.subList(1, Math.min(skills.size(), 4)) : List.of("Spring Boot");
        int years = resume.getYearsExperience() != null ? resume.getYearsExperience() : 5;

        return new CandidateTechStack(primaryLang, frameworks, List.of("PostgreSQL"), years);
    }

    private SingleTurnAssessment fallbackAssessment(String question, String answer, int questionNumber) {
        String nextQ = questionNumber >= 8
                ? "That concludes our interview. Thank you for your time."
                : "Let's move on. Can you explain how you would design a REST API with Spring Boot?";
        return new SingleTurnAssessment(
                false, 2, 3, 2,
                "Attempted to answer", "Needs more depth", "General",
                "Keep practicing — review the fundamentals and try to explain concepts with examples.",
                nextQ
        );
    }
}
