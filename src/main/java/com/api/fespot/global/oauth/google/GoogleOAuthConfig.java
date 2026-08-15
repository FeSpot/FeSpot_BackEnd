package com.api.fespot.global.oauth.google;

import com.api.fespot.domain.auth.service.social.OAuthClient;
import com.api.fespot.global.oauth.OAuthJwkDecoderFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

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

    NimbusJwtDecoder googleJwtDecoder(String jwkSetUri) {
        return OAuthJwkDecoderFactory.create(jwkSetUri);
    }
}
