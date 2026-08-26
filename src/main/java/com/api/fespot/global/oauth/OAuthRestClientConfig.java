package com.api.fespot.global.oauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class OAuthRestClientConfig {

    /**
     * OAuth 제공자 API 공용 RestClient. 상태가 없으므로 제공자 간 공유해도 안전하며,
     * 제공자별로 JDK HttpClient(셀렉터 스레드·풀)를 중복 생성하지 않기 위해 하나만 둔다.
     */
    @Bean(name = "oauthRestClient")
    RestClient oauthRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
