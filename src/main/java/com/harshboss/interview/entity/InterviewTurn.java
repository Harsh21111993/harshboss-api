package com.harshboss.interview.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interview_turns")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class InterviewTurn {
    @Id @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false) private UUID sessionId;
    @Column(name = "question_number", nullable = false) private int questionNumber;
    @Column(name = "question_text", nullable = false) private String questionText;
    @Column(name = "answer_transcript", nullable = false) private String answerTranscript;
    @Column(name = "knowledge_score", nullable = false) private int knowledgeScore;
    @Column(name = "communication_score", nullable = false) private int communicationScore;
    @Column(name = "problem_solving_score", nullable = false) private int problemSolvingScore;
    @Column(name = "jev_pass_fail") private String jevPassFail;
    @Column(name = "jev_confidence") private BigDecimal jevConfidence;
    @Column(name = "weakness_topic") private String weaknessTopic;
    @Column(columnDefinition = "TEXT") private String strengths;
    @Column(columnDefinition = "TEXT") private String weaknesses;
    @Column(columnDefinition = "TEXT") private String feedback;
    @Column(name = "next_question") private String nextQuestion;
    @Column(name = "recorded_at", nullable = false) private Instant recordedAt;

    @PrePersist void onCreate() { if (recordedAt == null) recordedAt = Instant.now(); }
}
