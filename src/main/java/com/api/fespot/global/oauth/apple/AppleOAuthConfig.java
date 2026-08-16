package com.api.fespot.global.oauth.apple;

import com.api.fespot.domain.auth.service.social.OAuthClient;
import com.api.fespot.global.oauth.OAuthJwkDecoderFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

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

    NimbusJwtDecoder appleJwtDecoder(String jwkSetUri) {
        return OAuthJwkDecoderFactory.create(jwkSetUri);
    }
}
