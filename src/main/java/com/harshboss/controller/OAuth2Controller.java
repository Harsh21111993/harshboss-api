package com.harshboss.controller;

import com.harshboss.dto.OAuth2Dtos;
import com.harshboss.service.OAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * OAuth2 endpoints for connecting + disconnecting email providers.
 *
 * <p>Flow:
 * <ol>
 *   <li>GET  /api/oauth/providers         → check what's configured + connected</li>
 *   <li>GET  /api/oauth/connect/{provider} → returns the authorize URL; frontend redirects</li>
 *   <li>GET  /api/oauth/callback/{provider}?code=...&state=... → backend exchanges code, returns HTML</li>
 *   <li>POST /api/oauth/disconnect/{provider} → removes stored tokens</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/oauth")
@RequiredArgsConstructor
public class OAuth2Controller {

    private final OAuth2Service oauth2Service;

    @GetMapping("/providers")
    public ResponseEntity<OAuth2Dtos.OAuthProvidersResponse> providers() {
        return ResponseEntity.ok(oauth2Service.getStatus());
    }

    @GetMapping("/connect/{provider}")
    public ResponseEntity<OAuth2Dtos.OAuthConnectResponse> connect(@PathVariable String provider) {
        log.info("GET /api/oauth/connect/{}", provider);
        return ResponseEntity.ok(oauth2Service.getConnectUrl(provider));
    }

    @GetMapping(value = "/callback/{provider}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> callback(@PathVariable String provider,
                                            @RequestParam("code") String code,
                                            @RequestParam("state") String state) {
        log.info("GET /api/oauth/callback/{}", provider);
        String html = oauth2Service.handleCallback(provider, code, state);
        return ResponseEntity.ok(html);
    }

    @PostMapping("/disconnect/{provider}")
    public ResponseEntity<Void> disconnect(@PathVariable String provider) {
        oauth2Service.disconnect(provider);
        return ResponseEntity.noContent().build();
    }
}
