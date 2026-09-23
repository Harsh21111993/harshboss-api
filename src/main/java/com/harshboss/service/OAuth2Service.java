package com.harshboss.service;

import com.harshboss.dto.OAuth2Dtos;
import com.harshboss.entity.UserOauthToken;
import com.harshboss.entity.enums.OAuthProvider;
import com.harshboss.repository.UserOauthTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OAuth2 flow: build authorize URLs, exchange auth codes for tokens, refresh
 * expired tokens, and return valid access tokens for Graph/Gmail API calls.
 *
 * <p>Config is read from application.yml under {@code harshboss.oauth.microsoft.*}
 * and {@code harshboss.oauth.google.*}. Both providers use the authorization-code
 * flow with {@code offline_access} / {@code access_type=offline} so we receive
 * a refresh token.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2Service {

    private final UserOauthTokenRepository tokenRepository;
    private final UserService userService;
    private final WebClient.Builder webClientBuilder;

    // ── Microsoft config ──────────────────────────────────────────────────
    @Value("${harshboss.oauth.microsoft.client-id:}")     private String msClientId;
    @Value("${harshboss.oauth.microsoft.client-secret:}") private String msClientSecret;
    @Value("${harshboss.oauth.microsoft.redirect-uri:}")  private String msRedirectUri;
    @Value("${harshboss.oauth.microsoft.scopes:}")        private String msScopes;
    @Value("${harshboss.oauth.microsoft.authorize-url:}") private String msAuthorizeUrl;
    @Value("${harshboss.oauth.microsoft.token-url:}")    private String msTokenUrl;

    // ── Google config ─────────────────────────────────────────────────────
    @Value("${harshboss.oauth.google.client-id:}")        private String googleClientId;
    @Value("${harshboss.oauth.google.client-secret:}")    private String googleClientSecret;
    @Value("${harshboss.oauth.google.redirect-uri:}")     private String googleRedirectUri;
    @Value("${harshboss.oauth.google.scopes:}")           private String googleScopes;
    @Value("${harshboss.oauth.google.authorize-url:}")    private String googleAuthorizeUrl;
    @Value("${harshboss.oauth.google.token-url:}")        private String googleTokenUrl;

    // ── Angular UI URL (for OAuth2 redirect) ──────────────────────────────────
    @Value("${harshboss.ui.url:http://localhost:4200}")   private String uiUrl;

    // ═════════════════════════════════════════════════════════════════════
    //  Status
    // ═════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public OAuth2Dtos.OAuthProvidersResponse getStatus() {
        UUID userId = userService.requireCurrentUserId();
        Optional<UserOauthToken> ms = tokenRepository.findByUserIdAndProvider(userId, OAuthProvider.MICROSOFT);
        Optional<UserOauthToken> g  = tokenRepository.findByUserIdAndProvider(userId, OAuthProvider.GOOGLE);

        OAuth2Dtos.OAuthProviderStatus microsoft = new OAuth2Dtos.OAuthProviderStatus(
                isMsConfigured(), ms.isPresent(), ms.map(UserOauthToken::getEmailAddress).orElse(null));
        OAuth2Dtos.OAuthProviderStatus google = new OAuth2Dtos.OAuthProviderStatus(
                isGoogleConfigured(), g.isPresent(), g.map(UserOauthToken::getEmailAddress).orElse(null));

        return new OAuth2Dtos.OAuthProvidersResponse(microsoft, google);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Connect — build the authorize URL
    // ═════════════════════════════════════════════════════════════════════

    public OAuth2Dtos.OAuthConnectResponse getConnectUrl(String provider) {
        OAuthProvider p = parseProvider(provider);
        UUID userId = userService.requireCurrentUserId();

        String authorizeUrl = switch (p) {
            case MICROSOFT -> buildMsAuthorizeUrl(userId);
            case GOOGLE -> buildGoogleAuthorizeUrl(userId);
        };
        return new OAuth2Dtos.OAuthConnectResponse(p.name(), authorizeUrl);
    }

    private String buildMsAuthorizeUrl(UUID userId) {
        return msAuthorizeUrl
                + "?client_id=" + enc(msClientId)
                + "&redirect_uri=" + enc(msRedirectUri)
                + "&response_type=code"
                + "&scope=" + enc(msScopes)
                + "&state=" + userId  // simple state binding (local app)
                + "&prompt=consent";
    }

    private String buildGoogleAuthorizeUrl(UUID userId) {
        return googleAuthorizeUrl
                + "?client_id=" + enc(googleClientId)
                + "&redirect_uri=" + enc(googleRedirectUri)
                + "&response_type=code"
                + "&scope=" + enc(googleScopes)
                + "&state=" + userId
                + "&access_type=offline"     // force a refresh_token
                + "&prompt=consent";
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Callback — exchange auth code for tokens, store them
    // ═════════════════════════════════════════════════════════════════════

    @Transactional
    public String handleCallback(String providerStr, String code, String state) {
        OAuthProvider provider = parseProvider(providerStr);
        UUID userId;
        try {
            userId = UUID.fromString(state);
        } catch (IllegalArgumentException e) {
            return errorPage("Invalid state parameter — please reconnect from the profile page.");
        }

        try {
            Map<String, Object> tokenResp = exchangeCodeForTokens(provider, code);
            String accessToken  = (String) tokenResp.get("access_token");
            String refreshToken = (String) tokenResp.get("refresh_token");
            String tokenType    = (String) tokenResp.getOrDefault("token_type", "Bearer");
            String scope        = (String) tokenResp.get("scope");
            int expiresIn = tokenResp.get("expires_in") instanceof Number n ? n.intValue() : 3600;
            Instant expiresAt = Instant.now().plusSeconds(expiresIn);

            if (accessToken == null || accessToken.isBlank()) {
                return errorPage("The provider did not return an access token. Please try again.");
            }

            // Fetch the user's email address from the provider's userinfo endpoint
            String emailAddress = fetchEmailAddress(provider, accessToken);

            // Upsert the token row
            tokenRepository.findByUserIdAndProvider(userId, provider).ifPresent(tokenRepository::delete);
            UserOauthToken token = new UserOauthToken();
            token.setUserId(userId);
            token.setProvider(provider);
            token.setAccessToken(accessToken);
            token.setRefreshToken(refreshToken);
            token.setTokenType(tokenType);
            token.setExpiresAt(expiresAt);
            token.setScope(scope);
            token.setEmailAddress(emailAddress);
            tokenRepository.save(token);

            log.info("OAuth2 callback: user {} connected {} ({})", userId, provider, emailAddress);
            return successPage(providerStr, emailAddress);
        } catch (Exception e) {
            log.error("OAuth2 callback failed for {}: {}", provider, e.getMessage(), e);
            return errorPage("Connection failed: " + e.getMessage());
        }
    }

    /** Exchange the authorization code for access + refresh tokens. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> exchangeCodeForTokens(OAuthProvider provider, String code) {
        String tokenUrl;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", provider == OAuthProvider.MICROSOFT ? msRedirectUri : googleRedirectUri);

        if (provider == OAuthProvider.MICROSOFT) {
            tokenUrl = msTokenUrl;
            form.add("client_id", msClientId);
            form.add("client_secret", msClientSecret);
            form.add("scope", msScopes);
        } else {
            tokenUrl = googleTokenUrl;
            form.add("client_id", googleClientId);
            form.add("client_secret", googleClientSecret);
        }

        return webClientBuilder.build().post()
                .uri(tokenUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    /** Fetch the mailbox address from the provider (for display + dedup). */
    @SuppressWarnings("unchecked")
    private String fetchEmailAddress(OAuthProvider provider, String accessToken) {
        try {
            String url = provider == OAuthProvider.MICROSOFT
                    ? "https://graph.microsoft.com/v1.0/me"
                    : "https://www.googleapis.com/oauth2/v2/userinfo";
            Map<String, Object> resp = webClientBuilder.build().get()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            if (resp == null) return null;
            if (provider == OAuthProvider.MICROSOFT) {
                // Graph /me returns { "userPrincipalName": "user@outlook.com", "displayName": "..." }
                Object upn = resp.get("userPrincipalName");
                if (upn != null) return upn.toString();
            }
            // Google userinfo returns { "email": "user@gmail.com", ... }
            Object email = resp.get("email");
            return email != null ? email.toString() : null;
        } catch (Exception e) {
            log.warn("Could not fetch email address from {}: {}", provider, e.getMessage());
            return null;
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Get a valid (refreshed if needed) access token for API calls
    // ═════════════════════════════════════════════════════════════════════

    @Transactional
    public UserOauthToken getValidToken(OAuthProvider provider) {
        UUID userId = userService.requireCurrentUserId();
        UserOauthToken token = tokenRepository
                .findByUserIdAndProvider(userId, provider)
                .orElseThrow(() -> new IllegalStateException(
                        "No " + provider + " account is connected. Connect it via POST /api/oauth/connect/" + provider));

        if (token.isExpired()) {
            if (token.getRefreshToken() == null || token.getRefreshToken().isBlank()) {
                throw new IllegalStateException(
                        "The " + provider + " access token has expired and no refresh token was stored. "
                        + "Please reconnect your account via /api/oauth/connect/" + provider);
            }
            refreshToken(provider, token);
        }
        return token;
    }

    /** Use the stored refresh token to obtain a new access token; update the row. */
    @Transactional
    public void refreshToken(OAuthProvider provider, UserOauthToken token) {
        String tokenUrl;
        String clientId;
        String clientSecret;

        if (provider == OAuthProvider.MICROSOFT) {
            tokenUrl = msTokenUrl; clientId = msClientId; clientSecret = msClientSecret;
        } else {
            tokenUrl = googleTokenUrl; clientId = googleClientId; clientSecret = googleClientSecret;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", token.getRefreshToken());
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        if (provider == OAuthProvider.MICROSOFT) {
            form.add("scope", msScopes);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = webClientBuilder.build().post()
                .uri(tokenUrl)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (resp == null || resp.get("access_token") == null) {
            throw new IllegalStateException("Token refresh failed for " + provider);
        }

        token.setAccessToken((String) resp.get("access_token"));
        String newRefresh = (String) resp.get("refresh_token");
        if (newRefresh != null && !newRefresh.isBlank()) {
            token.setRefreshToken(newRefresh);  // Google rotates the refresh token
        }
        int expiresIn = resp.get("expires_in") instanceof Number n ? n.intValue() : 3600;
        token.setExpiresAt(Instant.now().plusSeconds(expiresIn));
        tokenRepository.save(token);
        log.info("Refreshed {} access token for user {}", provider, token.getUserId());
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Disconnect
    // ═════════════════════════════════════════════════════════════════════

    @Transactional
    public void disconnect(String providerStr) {
        OAuthProvider provider = parseProvider(providerStr);
        UUID userId = userService.requireCurrentUserId();
        tokenRepository.deleteByUserIdAndProvider(userId, provider);
        log.info("Disconnected {} for user {}", provider, userId);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═════════════════════════════════════════════════════════════════════

    private boolean isMsConfigured() {
        return msClientId != null && !msClientId.isBlank();
    }

    private boolean isGoogleConfigured() {
        return googleClientId != null && !googleClientId.isBlank();
    }

    private OAuthProvider parseProvider(String s) {
        if (s == null) throw new IllegalArgumentException("Provider is required (microsoft or google)");
        return switch (s.toLowerCase()) {
            case "microsoft", "ms", "outlook" -> OAuthProvider.MICROSOFT;
            case "google", "gmail" -> OAuthProvider.GOOGLE;
            default -> throw new IllegalArgumentException("Unknown provider: " + s + " (use 'microsoft' or 'google')");
        };
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String successPage(String provider, String email) {
        // Redirect to the Angular UI (port 4200), not the backend (port 8080).
        // The UI_URL is read from application.yml (harshboss.ui.url, default http://localhost:4200).
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Connected</title>"
                + "<style>body{font-family:system-ui,sans-serif;display:flex;align-items:center;"
                + "justify-content:center;min-height:100vh;margin:0;background:#f3f4f5;color:#1f2122}"
                + ".card{text-align:center;padding:3rem}h1{color:#059669;font-size:3rem}p{color:#64748b}"
                + "</style></head><body><div class=\"card\">"
                + "<h1>✅</h1><h2>Connected!</h2>"
                + "<p>" + (email != null ? email : provider) + " is now connected.</p>"
                + "<p>Returning you to Harsh-Boss…</p>"
                + "<script>setTimeout(function(){window.location.href='" + uiUrl + "/profile';},1500);</script>"
                + "</div></body></html>";
    }

    private String errorPage(String message) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Connection failed</title>"
                + "<style>body{font-family:system-ui,sans-serif;display:flex;align-items:center;"
                + "justify-content:center;min-height:100vh;margin:0;background:#fef2f2;color:#991b1b}"
                + ".card{text-align:center;padding:3rem}h1{font-size:3rem}p{color:#7f1d1d}"
                + "</style></head><body><div class=\"card\">"
                + "<h1>❌</h1><h2>Connection failed</h2>"
                + "<p>" + message + "</p>"
                + "<p><a href=\"" + uiUrl + "/profile\" style=\"color:#059669\">← Back to Profile</a></p>"
                + "</div></body></html>";
    }
}
