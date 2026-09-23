package com.harshboss.dto;

/**
 * DTOs for the OAuth2 email-sync flow.
 */
public final class OAuth2Dtos {

    private OAuth2Dtos() {}

    /** Status of a single provider for the current user. */
    public record OAuthProviderStatus(boolean configured, boolean connected, String emailAddress) {}

    /** Status of both providers (returned by GET /api/oauth/providers). */
    public record OAuthProvidersResponse(OAuthProviderStatus microsoft, OAuthProviderStatus google) {}

    /** The authorize URL to redirect the user to (returned by GET /api/oauth/connect/{provider}). */
    public record OAuthConnectResponse(String provider, String authorizeUrl) {}

    /** Result of a sync operation (returned by POST /api/sync/emails). */
    public record EmailSyncResult(String provider, int fetched, int newEmails, int skipped, String message) {}
}
