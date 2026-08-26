package com.api.fespot.global.oauth.apple;

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
 * Apple 토큰 엔드포인트 실연동 구현. 교환·revoke 실패는 로그인/탈퇴 흐름을 막지 않도록
 * 예외를 밖으로 던지지 않고 empty/false로 보고한다.
 */
@Slf4j
public final class AppleAuthApiClient implements SocialUnlinkGateway {

    // social_credentials.provider_refresh_token 컬럼 길이와 일치해야 한다.
    static final int MAX_REFRESH_TOKEN_LENGTH = 1_024;
    private static final int MAX_AUTHORIZATION_CODE_LENGTH = 2_048;
    private static final JsonMapper JSON_MAPPER = new JsonMapper();

    private final RestClient restClient;
    private final String clientId;
    private final AppleClientSecretFactory clientSecretFactory;
    private final String tokenUri;
    private final String revokeUri;

    public AppleAuthApiClient(
            RestClient restClient,
            String clientId,
            AppleClientSecretFactory clientSecretFactory,
            String tokenUri,
            String revokeUri
    ) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecretFactory = clientSecretFactory;
        this.tokenUri = tokenUri;
        this.revokeUri = revokeUri;
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.APPLE;
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
        form.add("client_secret", clientSecretFactory.clientSecret());
        form.add("grant_type", "authorization_code");
        form.add("code", authorizationCode);

        AppleTokenResponse response;
        try {
            response = restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(AppleTokenResponse.class);
        } catch (RestClientResponseException exception) {
            log.warn("Apple authorization code 교환 실패. status={}", exception.getStatusCode());
            return Optional.empty();
        } catch (RestClientException exception) {
            log.warn("Apple 토큰 엔드포인트 연결 실패", exception);
            return Optional.empty();
        }

        if (response == null
                || isBlank(response.refreshToken())
                || response.refreshToken().length() > MAX_REFRESH_TOKEN_LENGTH) {
            log.warn("Apple 토큰 응답에 유효한 refresh token이 없습니다.");
            return Optional.empty();
        }
        if (!subjectMatches(response.idToken(), expectedProviderUserId)) {
            log.warn("Apple 토큰 응답의 id_token subject가 로그인 사용자와 다릅니다.");
            return Optional.empty();
        }
        return Optional.of(response.refreshToken());
    }

    @Override
    public boolean unlink(String providerUserId, String providerRefreshToken) {
        if (isBlank(providerRefreshToken)) {
            log.warn("Apple 토큰 revoke 불가 — 보관된 refresh token이 없습니다.");
            return false;
        }
        return revoke(providerRefreshToken);
    }

    boolean revoke(String refreshToken) {
        if (isBlank(refreshToken)) {
            return false;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecretFactory.clientSecret());
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");

        try {
            restClient.post()
                    .uri(revokeUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException exception) {
            log.warn("Apple 토큰 revoke 실패. status={}", exception.getStatusCode());
            return false;
        } catch (RestClientException exception) {
            log.warn("Apple revoke 엔드포인트 연결 실패", exception);
            return false;
        }
    }

    /**
     * 교환 응답의 id_token subject가 로그인한 Apple 사용자와 일치하는지 확인한다.
     * id_token은 Apple과의 TLS 직통 응답이므로 서명 재검증 없이 subject만 대조한다.
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
    record AppleTokenResponse(
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("id_token") String idToken
    ) {
    }
}
