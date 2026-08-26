package com.api.fespot.global.oauth.kakao;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.auth.service.port.SocialUnlinkGateway;
import com.api.fespot.domain.auth.service.social.OAuthClient;
import com.api.fespot.global.oauth.DisabledSocialUnlinkGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KakaoOAuthProperties.class)
public class KakaoOAuthConfig {

    @Bean(name = "kakaoOAuthClient")
    OAuthClient kakaoOAuthClient(
            KakaoOAuthProperties properties,
            @Qualifier("oauthRestClient") RestClient oauthRestClient
    ) {
        return new KakaoOAuthClient(
                oauthRestClient,
                properties.getAppId(),
                properties.getTokenInfoUri(),
                properties.getUserInfoUri()
        );
    }

    @Bean
    SocialUnlinkGateway kakaoUnlinkGateway(
            KakaoOAuthProperties properties,
            @Qualifier("oauthRestClient") RestClient oauthRestClient
    ) {
        if (!properties.isUnlinkConfigured()) {
            log.warn("Kakao unlink 비활성화 — oauth.kakao.admin-key를 설정해야 탈퇴 시 Kakao 연동 해제가 동작합니다.");
            return new DisabledSocialUnlinkGateway(OAuthProvider.KAKAO, "oauth.kakao.admin-key", false);
        }
        return new KakaoUnlinkClient(
                oauthRestClient,
                properties.getAdminKey(),
                properties.getUnlinkUri()
        );
    }
}
