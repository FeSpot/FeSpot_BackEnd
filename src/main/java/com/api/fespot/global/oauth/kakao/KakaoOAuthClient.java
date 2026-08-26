package com.api.fespot.global.oauth.kakao;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.auth.exception.InvalidOAuthTokenException;
import com.api.fespot.domain.auth.exception.OAuthProviderUnavailableException;
import com.api.fespot.domain.auth.service.social.OAuthClient;
import com.api.fespot.domain.auth.service.social.OAuthIdentity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.regex.Pattern;

public final class KakaoOAuthClient implements OAuthClient {

    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9\\-._~+/]+=*");

    private final RestClient restClient;
    private final long expectedAppId;
    private final String tokenInfoUri;
    private final String userInfoUri;

    public KakaoOAuthClient(
            RestClient restClient,
            long expectedAppId,
            String tokenInfoUri,
            String userInfoUri
    ) {
        this.restClient = restClient;
        this.expectedAppId = expectedAppId;
        this.tokenInfoUri = tokenInfoUri;
        this.userInfoUri = userInfoUri;
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    public OAuthIdentity authenticate(String credential) {
        if (credential == null
                || credential.length() > 4096
                || !BEARER_TOKEN_PATTERN.matcher(credential).matches()) {
            throw new InvalidOAuthTokenException();
        }

        KakaoTokenInfo tokenInfo;
        KakaoUserInfo response;
        try {
            tokenInfo = restClient.get()
                    .uri(tokenInfoUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential)
                    .retrieve()
                    .body(KakaoTokenInfo.class);
            validateTokenInfo(tokenInfo);
            response = restClient.get()
                    .uri(userInfoUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential)
                    .retrieve()
                    .body(KakaoUserInfo.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new InvalidOAuthTokenException();
            }
            throw new OAuthProviderUnavailableException();
        } catch (RestClientException exception) {
            throw new OAuthProviderUnavailableException();
        }
        return toIdentity(response, tokenInfo.id());
    }

    private void validateTokenInfo(KakaoTokenInfo tokenInfo) {
        if (tokenInfo == null
                || tokenInfo.id() == null
                || tokenInfo.id() <= 0
                || tokenInfo.expiresIn() == null
                || tokenInfo.expiresIn() <= 0
                || tokenInfo.appId() == null
                || tokenInfo.appId() <= 0
                || tokenInfo.appId() != expectedAppId) {
            throw new InvalidOAuthTokenException();
        }
    }

    private OAuthIdentity toIdentity(KakaoUserInfo response, Long expectedUserId) {
        String nickname = resolveNickname(response);
        if (response == null
                || response.id() == null
                || response.id() <= 0
                || !expectedUserId.equals(response.id())
                || response.account() == null
                || response.account().email() == null
                || response.account().email().isBlank()
                || !Boolean.TRUE.equals(response.account().emailValid())
                || !Boolean.TRUE.equals(response.account().emailVerified())
                || nickname == null
                || nickname.isBlank()) {
            throw new InvalidOAuthTokenException();
        }

        return new OAuthIdentity(
                OAuthProvider.KAKAO,
                response.id().toString(),
                response.account().email(),
                nickname,
                true
        );
    }

    private String resolveNickname(KakaoUserInfo response) {
        if (response == null) {
            return null;
        }
        if (response.account() != null
                && response.account().profile() != null
                && response.account().profile().nickname() != null
                && !response.account().profile().nickname().isBlank()) {
            return response.account().profile().nickname();
        }
        return response.properties() == null ? null : response.properties().nickname();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoTokenInfo(
            Long id,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("app_id") Long appId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoUserInfo(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount account,
            KakaoProperties properties
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoAccount(
            String email,
            @JsonProperty("is_email_valid") Boolean emailValid,
            @JsonProperty("is_email_verified") Boolean emailVerified,
            KakaoProfile profile
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoProfile(String nickname) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoProperties(String nickname) {
    }
}
