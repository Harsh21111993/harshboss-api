package com.harshboss.interview.service;

import com.harshboss.aspect.annotation.AuditAction;
import com.harshboss.aspect.annotation.LogExecution;
import com.harshboss.aspect.annotation.MeasurePerformance;
import com.harshboss.dto.JsonUtil;
import com.harshboss.interview.dto.*;
import com.harshboss.interview.entity.InterviewSession;
import com.harshboss.interview.entity.InterviewTurn;
import com.harshboss.interview.repository.InterviewSessionRepository;
import com.harshboss.interview.repository.InterviewTurnRepository;
import com.harshboss.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * InterviewService — manages interview sessions, tracks progress, and analyzes weaknesses.
 *
 * <p>Provides:</p>
 * <ul>
 *   <li>Start a new interview session (generates first question from resume)</li>
 *   <li>Submit an answer → get evaluation + next question</li>
 *   <li>Complete a session → generates summary + improvement roadmap</li>
 *   <li>View interview history + weakness analysis across sessions</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewAiService aiService;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewTurnRepository turnRepository;
    private final UserService userService;

    private static final int MAX_QUESTIONS = 10;

    /**
     * Start a new interview session.
     */
    @Transactional
    @AuditAction(action = "INTERVIEW_START", description = "Started a new AI voice interview")
    @LogExecution(level = "INFO")
    public InterviewSessionDto startInterview(UUID resumeId) {
        UUID userId = userService.requireCurrentUserId();

        CandidateTechStack stack = aiService.getTechStackFromResume(resumeId);
        String firstQuestion = aiService.generateFirstQuestion(stack);

        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setResumeId(resumeId);
        session.setTechStack(JsonUtil.getObjectMapper().writeValueAsString(stack));  // may throw
        session.setStatus("IN_PROGRESS");
        session.setStartedAt(Instant.now());
        session = sessionRepository.save(session);

        log.info("Interview session started: {} for user {} (stack: {})",
                session.getId(), userId, stack.primaryLanguage());

        return new InterviewSessionDto(
                session.getId().toString(),
                firstQuestion,
                stack.primaryLanguage() + " + " + String.join(", ", stack.frameworks()),
                "IN_PROGRESS"
        );
    }

    /**
     * Submit an answer → get evaluation + next question.
     */
    @Transactional
    @MeasurePerformance(warnThresholdMs = 15000)
    @AuditAction(action = "INTERVIEW_ANSWER", description = "Submitted interview answer")
    public SubmitAnswerResponse submitAnswer(String sessionId, String answerTranscript) {
        UUID sessionUuid = UUID.fromString(sessionId);
        InterviewSession session = sessionRepository.findById(sessionUuid)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        CandidateTechStack stack = JsonUtil.getObjectMapper().readValue(session.getTechStack(), CandidateTechStack.class);  // may throw

        List<InterviewTurn> existingTurns = turnRepository.findBySessionIdOrderByQuestionNumberAsc(sessionUuid);
        int questionNumber = existingTurns.size() + 1;

        // Get the current question (from the last turn's nextQuestion, or the first question)
        String currentQuestion;
        if (existingTurns.isEmpty()) {
            currentQuestion = aiService.generateFirstQuestion(stack); // shouldn't happen — first Q is returned at start
        } else {
            currentQuestion = existingTurns.get(existingTurns.size() - 1).getNextQuestion();
        }

        // Evaluate the answer using AI (Gemini + Jev)
        SingleTurnAssessment assessment = aiService.evaluateAndGenerateNext(
                stack, currentQuestion, answerTranscript, questionNumber);

        // Save the turn
        InterviewTurn turn = new InterviewTurn();
        turn.setSessionId(sessionUuid);
        turn.setQuestionNumber(questionNumber);
        turn.setQuestionText(currentQuestion);
        turn.setAnswerTranscript(answerTranscript);
        turn.setKnowledgeScore(assessment.knowledgeScore());
        turn.setCommunicationScore(assessment.communicationScore());
        turn.setProblemSolvingScore(assessment.problemSolvingScore());
        turn.setJevPassFail(assessment.answerSatisfactory() ? "PASS" : "FAIL");
        turn.setJevConfidence(BigDecimal.valueOf(0.85));
        turn.setWeaknessTopic(assessment.specificTopicFlaw());
        turn.setStrengths(assessment.strengths());
        turn.setWeaknesses(assessment.weaknesses());
        turn.setFeedback(assessment.feedbackToUser());
        turn.setNextQuestion(assessment.nextQuestion());
        turnRepository.save(turn);

        // Check if interview is complete
        boolean complete = questionNumber >= MAX_QUESTIONS ||
                assessment.nextQuestion().toLowerCase().contains("concludes") ||
                assessment.nextQuestion().toLowerCase().contains("thank you for your time");

        if (complete) {
            completeSession(session, sessionUuid);
        }

        return new SubmitAnswerResponse(
                questionNumber,
                assessment.knowledgeScore(),
                assessment.communicationScore(),
                assessment.problemSolvingScore(),
                assessment.answerSatisfactory() ? "PASS" : "FAIL",
                0.85,
                assessment.specificTopicFlaw(),
                assessment.feedbackToUser(),
                assessment.nextQuestion(),
                complete
        );
    }

    /** Mark a session as completed and generate the summary. */
    @Transactional
    public void completeSession(InterviewSession session, UUID sessionUuid) {
        List<InterviewTurn> turns = turnRepository.findBySessionIdOrderByQuestionNumberAsc(sessionUuid);

        double avgScore = turns.stream()
                .mapToInt(t -> (t.getKnowledgeScore() + t.getCommunicationScore() + t.getProblemSolvingScore()) / 3)
                .average().orElse(0);

        session.setOverallScore(BigDecimal.valueOf(avgScore).setScale(2, RoundingMode.HALF_UP));
        session.setStatus("COMPLETED");
        session.setCompletedAt(Instant.now());

        // Collect weakness tags
        List<String> weaknessTags = turns.stream()
                .map(InterviewTurn::getWeaknessTopic)
                .filter(Objects::nonNull)
                .filter(w -> !w.equals("none") && !w.equals("General"))
                .distinct()
                .toList();
        session.setWeaknessTags(JsonUtil.toJson(weaknessTags));

        // Generate session summary
        CandidateTechStack stack;
        try {
            stack = JsonUtil.getObjectMapper().readValue(session.getTechStack(), CandidateTechStack.class);
        } catch (Exception e) {
            stack = new CandidateTechStack("Java", List.of("Spring Boot"), List.of("PostgreSQL"), 5);
        }

        List<String> qaList = new ArrayList<>();
        for (InterviewTurn t : turns) {
            qaList.add(t.getQuestionText());
            qaList.add(t.getAnswerTranscript());
        }
        List<Integer> scores = turns.stream()
                .map(t -> (t.getKnowledgeScore() + t.getCommunicationScore() + t.getProblemSolvingScore()) / 3)
                .toList();

        String summary = aiService.generateSessionSummary(stack, qaList, scores);
        session.setSummary(summary);

        sessionRepository.save(session);
        log.info("Interview completed: {} — avg score: {}/5, weaknesses: {}",
                sessionUuid, avgScore, weaknessTags);
    }

    /** Get interview history for the current user. */
    @Transactional(readOnly = true)
    public List<InterviewHistoryDto> getHistory() {
        UUID userId = userService.requireCurrentUserId();
        List<InterviewSession> sessions = sessionRepository.findByUserIdOrderByStartedAtDesc(userId);

        List<InterviewHistoryDto> result = new ArrayList<>();
        for (InterviewSession s : sessions) {
            List<InterviewTurn> turns = turnRepository.findBySessionIdOrderByQuestionNumberAsc(s.getId());
            List<String> weaknessTags = JsonUtil.fromJson(s.getWeaknessTags());

            result.add(new InterviewHistoryDto(
                    s.getId().toString(),
                    s.getStartedAt() != null ? s.getStartedAt().toString() : "",
                    s.getStatus(),
                    s.getOverallScore() != null ? s.getOverallScore().doubleValue() : null,
                    turns.size(),
                    weaknessTags
            ));
        }
        return result;
    }

    /** Get weakness analysis across all sessions. */
    @Transactional(readOnly = true)
    public List<WeaknessAnalysis> getWeaknessAnalysis() {
        UUID userId = userService.requireCurrentUserId();
        List<Object[]> rows = turnRepository.findRecurringWeaknesses(userId);

        List<WeaknessAnalysis> result = new ArrayList<>();
        for (Object[] row : rows) {
            String topic = (String) row[0];
            int count = ((Number) row[1]).intValue();
            double avgScore = ((Number) row[2]).doubleValue();

            String recommendation = generateRecommendation(topic, avgScore);
            result.add(new WeaknessAnalysis(topic, count, avgScore, recommendation));
        }
        return result;
    }

    /** Get the full transcript of a specific session. */
    @Transactional(readOnly = true)
    public Map<String, Object> getSessionDetail(String sessionId) {
        UUID sessionUuid = UUID.fromString(sessionId);
        InterviewSession session = sessionRepository.findById(sessionUuid)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        List<InterviewTurn> turns = turnRepository.findBySessionIdOrderByQuestionNumberAsc(sessionUuid);

        return Map.of(
                "session", Map.of(
                        "id", session.getId().toString(),
                        "status", session.getStatus(),
                        "overallScore", session.getOverallScore() != null ? session.getOverallScore().doubleValue() : 0,
                        "startedAt", session.getStartedAt() != null ? session.getStartedAt().toString() : "",
                        "completedAt", session.getCompletedAt() != null ? session.getCompletedAt().toString() : "",
                        "summary", session.getSummary() != null ? session.getSummary() : "",
                        "weaknessTags", JsonUtil.fromJson(session.getWeaknessTags())
                ),
                "turns", turns.stream().map(t -> Map.of(
                        "questionNumber", t.getQuestionNumber(),
                        "question", t.getQuestionText(),
                        "answer", t.getAnswerTranscript(),
                        "knowledgeScore", t.getKnowledgeScore(),
                        "communicationScore", t.getCommunicationScore(),
                        "problemSolvingScore", t.getProblemSolvingScore(),
                        "weaknessTopic", t.getWeaknessTopic() != null ? t.getWeaknessTopic() : "none",
                        "feedback", t.getFeedback() != null ? t.getFeedback() : ""
                )).toList()
        );
    }

    private String generateRecommendation(String topic, double avgScore) {
        if (avgScore <= 1.5) {
            return "Critical weakness. Study this topic thoroughly before your next interview. " +
                   "Read the official documentation and practice explaining it out loud.";
        } else if (avgScore <= 2.5) {
            return "Needs improvement. Review the fundamentals and practice answering questions on this topic.";
        } else {
            return "On the right track. Keep practicing — try explaining this topic to someone else.";
        }
    }
}
