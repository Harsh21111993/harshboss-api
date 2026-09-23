package com.harshboss.interview.dto;

import java.util.List;

/** DTOs for the AI Voice Interviewer. */

/** The candidate's tech stack (extracted from resume). */
public record CandidateTechStack(
    String primaryLanguage,
    List<String> frameworks,
    List<String> databases,
    int yearsOfExperience
) {}

/** Assessment of a single interview turn (question + answer). */
public record SingleTurnAssessment(
    boolean answerSatisfactory,
    int knowledgeScore,       // 1-5
    int communicationScore,   // 1-5
    int problemSolvingScore,  // 1-5
    String strengths,
    String weaknesses,
    String specificTopicFlaw, // e.g. "Spring Transaction Propagation"
    String feedbackToUser,
    String nextQuestion
) {}

/** Request to start a new interview session. */
public record StartInterviewRequest(UUID resumeId) {}

/** Response when an interview session starts. */
public record InterviewSessionDto(
    String sessionId,
    String firstQuestion,
    String techStack,
    String status
) {}

/** Request to submit an answer and get the next question. */
public record SubmitAnswerRequest(
    String sessionId,
    String answerTranscript
) {}

/** Response after submitting an answer. */
public record SubmitAnswerResponse(
    int questionNumber,
    int knowledgeScore,
    int communicationScore,
    int problemSolvingScore,
    String jevPassFail,
    double jevConfidence,
    String weaknessTopic,
    String feedback,
    String nextQuestion,
    boolean interviewComplete
) {}

/** Weakness analysis result. */
public record WeaknessAnalysis(
    String topic,
    int incidentCount,
    double avgScore,
    String recommendation
) {}

/** Interview history item. */
public record InterviewHistoryDto(
    String sessionId,
    String startedAt,
    String status,
    Double overallScore,
    int totalQuestions,
    List<String> weaknessTags
) {}

import java.util.UUID;
