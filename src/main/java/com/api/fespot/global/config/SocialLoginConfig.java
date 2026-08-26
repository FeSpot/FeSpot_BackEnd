package com.api.fespot.global.config;

import com.api.fespot.domain.auth.repository.RefreshSessionStore;
import com.api.fespot.domain.auth.repository.SocialAccountPort;
import com.api.fespot.domain.auth.repository.jpa.JpaSocialAccountAdapter;
import com.api.fespot.domain.auth.repository.jpa.SocialCredentialRepository;
import com.api.fespot.domain.auth.service.port.AuthTokenIssuer;
import com.api.fespot.domain.auth.service.port.SocialUnlinkGateway;
import com.api.fespot.domain.auth.service.social.OAuthClient;
import com.api.fespot.domain.auth.service.social.OAuthClientResolver;
import com.api.fespot.domain.auth.service.social.SocialLoginService;
import com.api.fespot.domain.auth.service.social.SocialLoginUseCase;
import com.api.fespot.domain.auth.service.social.SocialUnlinkGatewayResolver;
import com.api.fespot.domain.user.repository.UserRepository;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SocialLoginConfig {

    @Bean
    public OAuthClientResolver oAuthClientResolver(List<OAuthClient> clients) {
        return new OAuthClientResolver(clients);
    }

    @Bean
    public SocialUnlinkGatewayResolver socialUnlinkGatewayResolver(List<SocialUnlinkGateway> gateways) {
        return new SocialUnlinkGatewayResolver(gateways);
    }

    @Bean
    public SocialAccountPort socialAccountPort(
            UserRepository userRepository,
            SocialCredentialRepository socialCredentialRepository
    ) {
        return new JpaSocialAccountAdapter(userRepository, socialCredentialRepository);
    }

    @Bean
    public SocialLoginUseCase socialLoginUseCase(
            OAuthClientResolver oAuthClientResolver,
            SocialAccountPort socialAccountPort,
            AuthTokenIssuer authTokenIssuer,
            RefreshSessionStore refreshSessionStore,
            SocialUnlinkGatewayResolver socialUnlinkGatewayResolver
    ) {
        return new SocialLoginService(
                oAuthClientResolver,
                socialAccountPort,
                authTokenIssuer,
                refreshSessionStore,
                socialUnlinkGatewayResolver);
    }
}
