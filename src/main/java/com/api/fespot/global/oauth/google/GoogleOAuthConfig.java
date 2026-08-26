package com.api.fespot.global.oauth.google;

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
@EnableConfigurationProperties(GoogleOAuthProperties.class)
public class GoogleOAuthConfig {

    @Bean(name = "googleOAuthClientV1")
    OAuthClient googleOAuthClientV1(GoogleOAuthProperties properties) {
        return new GoogleOAuthClient(
                properties.getClientId(),
                googleJwtDecoder(properties.getJwkSetUri())
        );
    }

    @Bean
    SocialUnlinkGateway googleUnlinkGateway(
            GoogleOAuthProperties properties,
            @Qualifier("oauthRestClient") RestClient oauthRestClient
    ) {
        if (!properties.isRevocationConfigured()) {
            log.warn("Google 토큰 revoke 비활성화 — oauth.google.client-secret을 설정해야 "
                    + "탈퇴 시 Google 연동 해제가 동작합니다.");
            return new DisabledSocialUnlinkGateway(OAuthProvider.GOOGLE, "oauth.google.client-secret", true);
        }
        return new GoogleAuthApiClient(
                oauthRestClient,
                properties.getClientId(),
                properties.getClientSecret(),
                properties.getTokenUri(),
                properties.getRevokeUri()
        );
    }

    NimbusJwtDecoder googleJwtDecoder(String jwkSetUri) {
        return OAuthJwkDecoderFactory.create(jwkSetUri);
    }
}
