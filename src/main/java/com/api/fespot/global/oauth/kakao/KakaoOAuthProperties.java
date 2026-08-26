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

    // 검증 애너테이션을 붙이지 않는다: 바인딩 검증 실패 메시지가 rejected value로 키를 로그에 남긴다.
    // 있어야 탈퇴 시 회원번호 기반 unlink가 활성화된다.
    private String adminKey;

    @NotBlank
    private String tokenInfoUri = "https://kapi.kakao.com/v1/user/access_token_info";

    @NotBlank
    private String userInfoUri = "https://kapi.kakao.com/v2/user/me";

    @NotBlank
    private String unlinkUri = "https://kapi.kakao.com/v1/user/unlink";

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

    public String getAdminKey() {
        return adminKey;
    }

    public void setAdminKey(String adminKey) {
        this.adminKey = adminKey;
    }

    public String getUnlinkUri() {
        return unlinkUri;
    }

    public void setUnlinkUri(String unlinkUri) {
        this.unlinkUri = unlinkUri;
    }

    public boolean isUnlinkConfigured() {
        return adminKey != null && !adminKey.isBlank();
    }

    @AssertTrue(message = "Kakao user-info URI must be the trusted Kakao endpoint")
    public boolean isTrustedUserInfoUri() {
        return isTrustedKakaoUri(userInfoUri, "/v2/user/me");
    }

    @AssertTrue(message = "Kakao unlink URI must be the trusted Kakao endpoint")
    public boolean isTrustedUnlinkUri() {
        return isTrustedKakaoUri(unlinkUri, "/v1/user/unlink");
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
