package com.api.fespot.global.oauth.apple;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;

@ConfigurationProperties(prefix = "oauth.apple")
@Validated
public class AppleOAuthProperties {

    private static final String DEFAULT_JWK_SET_URI = "https://appleid.apple.com/auth/keys";
    private static final String DEFAULT_TOKEN_URI = "https://appleid.apple.com/auth/token";
    private static final String DEFAULT_REVOKE_URI = "https://appleid.apple.com/auth/revoke";

    @NotBlank(message = "Apple OAuth client ID must not be blank")
    @Size(max = 255)
    private String clientId;

    @NotBlank(message = "Apple JWK Set URI must not be blank")
    @Size(max = 2_048)
    private String jwkSetUri = DEFAULT_JWK_SET_URI;

    // 아래 3개는 client_secret(ES256) 서명용. 전부 있어야 토큰 교환·revoke가 활성화된다.
    @Size(max = 255)
    private String teamId;

    @Size(max = 255)
    private String keyId;

    // 검증 애너테이션을 붙이지 않는다: 바인딩 검증 실패 메시지가 rejected value로 키 원문을 로그에 남긴다.
    // 형식 오류는 AppleClientSecretFactory 파싱 단계에서 값 노출 없이 걸러진다.
    private String privateKey;

    @NotBlank(message = "Apple token URI must not be blank")
    @Size(max = 2_048)
    private String tokenUri = DEFAULT_TOKEN_URI;

    @NotBlank(message = "Apple revoke URI must not be blank")
    @Size(max = 2_048)
    private String revokeUri = DEFAULT_REVOKE_URI;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
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
        return hasText(teamId) && hasText(keyId) && hasText(privateKey);
    }

    @AssertTrue(message = "Apple JWK Set URI must be the canonical HTTPS endpoint")
    public boolean isTrustedJwkSetUri() {
        return isTrustedAppleUri(jwkSetUri, "/auth/keys");
    }

    @AssertTrue(message = "Apple token URI must be the canonical HTTPS endpoint")
    public boolean isTrustedTokenUri() {
        return isTrustedAppleUri(tokenUri, "/auth/token");
    }

    @AssertTrue(message = "Apple revoke URI must be the canonical HTTPS endpoint")
    public boolean isTrustedRevokeUri() {
        return isTrustedAppleUri(revokeUri, "/auth/revoke");
    }

    private static boolean isTrustedAppleUri(String value, String expectedPath) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "appleid.apple.com".equalsIgnoreCase(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && expectedPath.equals(uri.getPath())
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
