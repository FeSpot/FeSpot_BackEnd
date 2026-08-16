package com.api.fespot.global.oauth.kakao;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;

@ConfigurationProperties(prefix = "oauth.kakao")
@Validated
public class KakaoOAuthProperties {

    @NotNull
    @Positive
    private Long appId;

    @NotBlank
    private String tokenInfoUri = "https://kapi.kakao.com/v1/user/access_token_info";

    @NotBlank
    private String userInfoUri = "https://kapi.kakao.com/v2/user/me";

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public String getTokenInfoUri() {
        return tokenInfoUri;
    }

    public void setTokenInfoUri(String tokenInfoUri) {
        this.tokenInfoUri = tokenInfoUri;
    }

    public String getUserInfoUri() {
        return userInfoUri;
    }

    public void setUserInfoUri(String userInfoUri) {
        this.userInfoUri = userInfoUri;
    }

    @AssertTrue(message = "Kakao user-info URI must be the trusted Kakao endpoint")
    public boolean isTrustedUserInfoUri() {
        return isTrustedKakaoUri(userInfoUri, "/v2/user/me");
    }

    @AssertTrue(message = "Kakao token-info URI must be the trusted Kakao endpoint")
    public boolean isTrustedTokenInfoUri() {
        return isTrustedKakaoUri(tokenInfoUri, "/v1/user/access_token_info");
    }

    private boolean isTrustedKakaoUri(String value, String expectedPath) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "kapi.kakao.com".equalsIgnoreCase(uri.getHost())
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
