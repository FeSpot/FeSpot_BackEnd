package com.api.fespot.global.oauth.kakao;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.auth.service.port.SocialUnlinkGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.regex.Pattern;

/**
 * Kakao 회원 탈퇴용 unlink 실연동 구현. Admin Key로 회원번호(target_id) 기반 연결 해제를
 * 호출하므로 로그인 시 토큰을 보관할 필요가 없다.
 * 실패는 탈퇴 흐름을 막지 않도록 예외를 밖으로 던지지 않고 false로 보고한다.
 */
@Slf4j
public final class KakaoUnlinkClient implements SocialUnlinkGateway {

    // Kakao 회원번호는 양의 정수. providerUserId가 이 형식이 아니면 API 호출 없이 실패 처리한다.
    private static final Pattern KAKAO_USER_ID_PATTERN = Pattern.compile("[1-9][0-9]{0,18}");

    private final RestClient restClient;
    private final String adminKey;
    private final String unlinkUri;

    public KakaoUnlinkClient(RestClient restClient, String adminKey, String unlinkUri) {
        this.restClient = restClient;
        this.adminKey = adminKey;
        this.unlinkUri = unlinkUri;
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.KAKAO;
    }

    @Override
    public boolean unlink(String providerUserId, String providerRefreshToken) {
        if (providerUserId == null || !KAKAO_USER_ID_PATTERN.matcher(providerUserId).matches()) {
            log.warn("Kakao unlink 불가 — 저장된 provider user id가 회원번호 형식이 아닙니다.");
            return false;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", providerUserId);

        try {
            restClient.post()
                    .uri(unlinkUri)
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + adminKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException exception) {
            log.warn("Kakao unlink 실패. status={}", exception.getStatusCode());
            return false;
        } catch (RestClientException exception) {
            log.warn("Kakao unlink 엔드포인트 연결 실패", exception);
            return false;
        }
    }
}
