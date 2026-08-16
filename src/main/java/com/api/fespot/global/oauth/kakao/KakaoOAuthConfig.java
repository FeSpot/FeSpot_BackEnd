package com.api.fespot.global.oauth.kakao;

import com.api.fespot.domain.auth.service.social.OAuthClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KakaoOAuthProperties.class)
public class KakaoOAuthConfig {

    @Bean(name = "kakaoOAuthClient")
    OAuthClient kakaoOAuthClient(KakaoOAuthProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        return new KakaoOAuthClient(
                restClient,
                properties.getAppId(),
                properties.getTokenInfoUri(),
                properties.getUserInfoUri()
        );
    }
}
