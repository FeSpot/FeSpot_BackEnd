package com.api.fespot.global.oauth.apple;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.auth.service.port.SocialUnlinkGateway;
import com.api.fespot.domain.auth.service.social.OAuthClient;
import com.api.fespot.global.oauth.DisabledSocialUnlinkGateway;
import com.api.fespot.global.oauth.OAuthJwkDecoderFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppleOAuthProperties.class)
public class AppleOAuthConfig {

    @Bean(name = "appleOAuthClient")
    OAuthClient appleOAuthClientV1(AppleOAuthProperties properties) {
        return new AppleOAuthClient(
                properties.getClientId(),
                appleJwtDecoder(properties.getJwkSetUri())
        );
    }

    @Bean
    SocialUnlinkGateway appleUnlinkGateway(
            AppleOAuthProperties properties,
            @Qualifier("oauthRestClient") RestClient oauthRestClient
    ) {
        if (!properties.isRevocationConfigured()) {
            log.warn("Apple 토큰 revoke 비활성화 — oauth.apple.team-id/key-id/private-key를 설정해야 "
                    + "탈퇴 시 Apple 토큰 revoke(앱스토어 심사 요건)가 동작합니다.");
            return new DisabledSocialUnlinkGateway(
                    OAuthProvider.APPLE, "oauth.apple.team-id/key-id/private-key", true);
        }
        AppleClientSecretFactory clientSecretFactory = AppleClientSecretFactory.create(
                properties.getTeamId(),
                properties.getClientId(),
                properties.getKeyId(),
                properties.getPrivateKey()
        );
        return new AppleAuthApiClient(
                oauthRestClient,
                properties.getClientId(),
                clientSecretFactory,
                properties.getTokenUri(),
                properties.getRevokeUri()
        );
    }

    NimbusJwtDecoder appleJwtDecoder(String jwkSetUri) {
        return OAuthJwkDecoderFactory.create(jwkSetUri);
    }
}
