package com.api.fespot.global.oauth.google;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.auth.service.port.SocialUnlinkGateway;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Google 토큰 엔드포인트 실연동 구현. 로그인 시 serverAuthCode를 refresh token으로 교환해
 * 보관해 두었다가, 탈퇴 시 revoke 엔드포인트로 부여(grant) 전체를 무효화한다.
 * 교환·revoke 실패는 로그인/탈퇴 흐름을 막지 않도록 예외를 밖으로 던지지 않고 empty/false로 보고한다.
 */
@Slf4j
public final class GoogleAuthApiClient implements SocialUnlinkGateway {

    // social_credentials.provider_refresh_token 컬럼 길이와 일치해야 한다.
    static final int MAX_REFRESH_TOKEN_LENGTH = 1_024;
    private static final int MAX_AUTHORIZATION_CODE_LENGTH = 2_048;
    private static final JsonMapper JSON_MAPPER = new JsonMapper();

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUri;
    private final String revokeUri;

    public GoogleAuthApiClient(
            RestClient restClient,
            String clientId,
            String clientSecret,
            String tokenUri,
            String revokeUri
    ) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUri = tokenUri;
        this.revokeUri = revokeUri;
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.GOOGLE;
    }

    @Override
    public boolean usesStoredRefreshToken() {
        return true;
    }

    @Override
    public Optional<String> exchangeCodeForRefreshToken(String authorizationCode, String expectedProviderUserId) {
        if (authorizationCode == null || authorizationCode.isBlank()
                || authorizationCode.length() > MAX_AUTHORIZATION_CODE_LENGTH) {
            return Optional.empty();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "authorization_code");
        form.add("code", authorizationCode);
        // 모바일 SDK serverAuthCode 교환 규격: redirect_uri는 빈 값으로 보낸다 (Google iOS/Android 문서 예시와 동일)
        form.add("redirect_uri", "");

        GoogleTokenResponse response;
        try {
            response = restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
        } catch (RestClientResponseException exception) {
            log.warn("Google authorization code 교환 실패. status={}", exception.getStatusCode());
            return Optional.empty();
        } catch (RestClientException exception) {
            log.warn("Google 토큰 엔드포인트 연결 실패", exception);
            return Optional.empty();
        }

        if (response == null
                || isBlank(response.refreshToken())
                || response.refreshToken().length() > MAX_REFRESH_TOKEN_LENGTH) {
            // access_type=offline 미요청 등으로 refresh token이 없는 응답도 여기서 걸러진다.
            log.warn("Google 토큰 응답에 유효한 refresh token이 없습니다.");
            return Optional.empty();
        }
        if (!subjectMatches(response.idToken(), expectedProviderUserId)) {
            log.warn("Google 토큰 응답의 id_token subject가 로그인 사용자와 다릅니다.");
            return Optional.empty();
        }
        return Optional.of(response.refreshToken());
    }

    @Override
    public boolean unlink(String providerUserId, String providerRefreshToken) {
        if (isBlank(providerRefreshToken)) {
            log.warn("Google 토큰 revoke 불가 — 보관된 refresh token이 없습니다.");
            return false;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", providerRefreshToken);

        try {
            restClient.post()
                    .uri(revokeUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException exception) {
            log.warn("Google 토큰 revoke 실패. status={}", exception.getStatusCode());
            return false;
        } catch (RestClientException exception) {
            log.warn("Google revoke 엔드포인트 연결 실패", exception);
            return false;
        }
    }

    /**
     * 교환 응답의 id_token subject가 로그인한 Google 사용자와 일치하는지 확인한다.
     * id_token은 Google과의 TLS 직통 응답이므로 서명 재검증 없이 subject만 대조한다.
     */
    private boolean subjectMatches(String idToken, String expectedProviderUserId) {
        if (isBlank(idToken) || isBlank(expectedProviderUserId)) {
            return false;
        }
        String[] segments = idToken.split("\\.", -1);
        if (segments.length != 3) {
            return false;
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(segments[1]);
            JsonNode claims = JSON_MAPPER.readTree(new String(payload, StandardCharsets.UTF_8));
            return expectedProviderUserId.equals(claims.path("sub").textValue());
        } catch (IllegalArgumentException | IOException exception) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoogleTokenResponse(
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("id_token") String idToken
    ) {
    }
}
