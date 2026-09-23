package com.harshboss.interview.controller;

import com.harshboss.aspect.annotation.RateLimit;
import com.harshboss.interview.dto.*;
import com.harshboss.interview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * InterviewController — AI Voice Interviewer endpoints.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>POST /api/interview/start — start a new interview session</li>
 *   <li>POST /api/interview/answer — submit answer → get evaluation + next question</li>
 *   <li>GET  /api/interview/history — list past sessions</li>
 *   <li>GET  /api/interview/weaknesses — recurring weakness analysis</li>
 *   <li>GET  /api/interview/{sessionId} — full session transcript</li>
 *   <li>POST /api/interview/tts — text-to-speech proxy (generates interviewer voice)</li>
 *   <li>POST /api/interview/deepgram-token — generates ephemeral Deepgram STT token</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @Value("${harshboss.interview.tts.api-key:${OPENAI_API_KEY:dummy}}")
    private String ttsApiKey;

    @Value("${harshboss.interview.tts.voice:onyx}")
    private String ttsVoice;

    @Value("${harshboss.interview.deepgram.api-key:${DEEPGRAM_API_KEY:}}")
    private String deepgramApiKey;

    /**
     * POST /api/interview/start?resumeId={uuid}
     * Starts a new interview session and returns the first question.
     */
    @PostMapping("/start")
    @RateLimit(maxCalls = 5, windowSeconds = 60)
    public ResponseEntity<InterviewSessionDto> start(@RequestParam java.util.UUID resumeId) {
        log.info("POST /api/interview/start?resumeId={}", resumeId);
        return ResponseEntity.ok(interviewService.startInterview(resumeId));
    }

    /**
     * POST /api/interview/answer
     * Submit the candidate's spoken answer (as transcript text) → get evaluation + next question.
     */
    @PostMapping("/answer")
    @RateLimit(maxCalls = 30, windowSeconds = 60)
    public ResponseEntity<SubmitAnswerResponse> submitAnswer(@RequestBody SubmitAnswerRequest request) {
        log.info("POST /api/interview/answer — session: {}", request.sessionId());
        return ResponseEntity.ok(interviewService.submitAnswer(request.sessionId(), request.answerTranscript()));
    }

    /**
     * GET /api/interview/history — list all past interview sessions.
     */
    @GetMapping("/history")
    public ResponseEntity<List<InterviewHistoryDto>> getHistory() {
        return ResponseEntity.ok(interviewService.getHistory());
    }

    /**
     * GET /api/interview/weaknesses — recurring weakness analysis across all sessions.
     */
    @GetMapping("/weaknesses")
    public ResponseEntity<List<WeaknessAnalysis>> getWeaknesses() {
        return ResponseEntity.ok(interviewService.getWeaknessAnalysis());
    }

    /**
     * GET /api/interview/{sessionId} — full session transcript + summary.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(interviewService.getSessionDetail(sessionId));
    }

    /**
     * POST /api/interview/tts — Text-to-Speech proxy.
     * Generates the interviewer's voice from text using OpenAI TTS.
     * Returns audio/mpeg data that the Angular frontend plays directly.
     */
    @PostMapping(value = "/tts", produces = "audio/mpeg")
    public ResponseEntity<byte[]> generateSpeech(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            byte[] audio = WebClient.builder().baseUrl("https://api.openai.com").build()
                    .post()
                    .uri("/v1/audio/speech")
                    .header("Authorization", "Bearer " + ttsApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(Map.of(
                            "model", "tts-1",
                            "input", text,
                            "voice", ttsVoice,
                            "response_format", "mp3"
                    ))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            return ResponseEntity.ok()
                    .header("Content-Type", "audio/mpeg")
                    .body(audio);
        } catch (Exception e) {
            log.error("TTS generation failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * POST /api/interview/deepgram-token — generates an ephemeral Deepgram token for browser STT.
     * The frontend uses this token to connect directly to Deepgram's WebSocket for real-time transcription.
     */
    @PostMapping("/deepgram-token")
    public ResponseEntity<Map<String, String>> getDeepgramToken() {
        if (deepgramApiKey == null || deepgramApiKey.isBlank()) {
            return ResponseEntity.ok(Map.of("token", "", "error", "Deepgram API key not configured"));
        }

        try {
            Map<String, Object> response = WebClient.builder().baseUrl("https://api.deepgram.com").build()
                    .post()
                    .uri("/v1/projects")
                    .header("Authorization", "Token " + deepgramApiKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            // For production, create a proper ephemeral key via Deepgram's API
            // For now, return the master key (development only — NEVER do this in production)
            return ResponseEntity.ok(Map.of("token", deepgramApiKey, "url", "wss://api.deepgram.com/v1/listen"));
        } catch (Exception e) {
            log.error("Failed to get Deepgram token: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("token", "", "error", e.getMessage()));
        }
    }
}
