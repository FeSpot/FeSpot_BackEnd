package com.api.fespot.global.oauth.google;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;

@ConfigurationProperties(prefix = "oauth.google")
@Validated
public class GoogleOAuthProperties {

    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String DEFAULT_REVOKE_URI = "https://oauth2.googleapis.com/revoke";

    @NotBlank
    @Size(max = 255)
    private String clientId;

    // 검증 애너테이션을 붙이지 않는다: 바인딩 검증 실패 메시지가 rejected value로 시크릿을 로그에 남긴다.
    // 있어야 authorization code 교환·revoke(탈퇴 시 연동 해제)가 활성화된다.
    private String clientSecret;

    @NotBlank
    private String jwkSetUri = "https://www.googleapis.com/oauth2/v3/certs";

    @NotBlank
    private String tokenUri = DEFAULT_TOKEN_URI;

    @NotBlank
    private String revokeUri = DEFAULT_REVOKE_URI;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public String getTokenUri() {
        return tokenUri;
    }

    public void setTokenUri(String tokenUri) {
        this.tokenUri = tokenUri;
    }

    public String getRevokeUri() {
        return revokeUri;
    }

    public void setRevokeUri(String revokeUri) {
        this.revokeUri = revokeUri;
    }

    public boolean isRevocationConfigured() {
        return clientSecret != null && !clientSecret.isBlank();
    }

    @AssertTrue(message = "Google JWK Set URI must be the trusted Google endpoint")
    public boolean isTrustedJwkSetUri() {
        return isTrustedGoogleUri(jwkSetUri, "www.googleapis.com", "/oauth2/v3/certs");
    }

    @AssertTrue(message = "Google token URI must be the trusted Google endpoint")
    public boolean isTrustedTokenUri() {
        return isTrustedGoogleUri(tokenUri, "oauth2.googleapis.com", "/token");
    }

    @AssertTrue(message = "Google revoke URI must be the trusted Google endpoint")
    public boolean isTrustedRevokeUri() {
        return isTrustedGoogleUri(revokeUri, "oauth2.googleapis.com", "/revoke");
    }

    private static boolean isTrustedGoogleUri(String value, String expectedHost, String expectedPath) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && expectedHost.equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getUserInfo() == null
                    && expectedPath.equals(uri.getPath())
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
