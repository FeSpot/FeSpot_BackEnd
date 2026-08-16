package com.api.fespot.domain.auth.service.social;

import com.api.fespot.domain.auth.enums.OAuthProvider;

public final class SocialLoginCommand {

    private final OAuthProvider provider;
    private final String credential;
    private final String expectedNonce;

    public SocialLoginCommand(OAuthProvider provider, String credential) {
        this(provider, credential, null);
    }

    /**
     * Creates a social-login command with the expected nonce for providers that bind
     * the credential to a nonce (Apple). Following the convention of the major FE
     * libraries (invertase RN / expo-apple-authentication), the client generates a raw
     * nonce, Apple embeds its SHA-256 hash in the identity token, and the raw nonce is
     * sent alongside the credential for the server to hash and compare.
     */
    public SocialLoginCommand(OAuthProvider provider, String credential, String expectedNonce) {
        this.provider = provider;
        this.credential = credential;
        this.expectedNonce = expectedNonce;
    }

    public OAuthProvider provider() {
        return provider;
    }

    public String credential() {
        return credential;
    }

    public String expectedNonce() {
        return expectedNonce;
    }

    @Override
    public String toString() {
        return "SocialLoginCommand[provider=" + provider
                + ", credential=[REDACTED], expectedNonce=[REDACTED]]";
    }
}
