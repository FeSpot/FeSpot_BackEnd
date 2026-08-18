package com.api.fespot.global.oauth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Resource;
import com.nimbusds.jose.util.ResourceRetriever;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class OAuthJwkDecoderFactory {

    private static final int MAX_JWK_SET_BYTES = 51_200;

    private OAuthJwkDecoderFactory() {
    }

    public static NimbusJwtDecoder create(String jwkSetUri) {
        RestTemplate restTemplate = redirectSafeRestTemplate();
        ResourceRetriever resourceRetriever = url -> retrieveJwkSet(restTemplate, URI.create(url.toString()));
        JWKSource<SecurityContext> jwkSource;
        try {
            jwkSource = JWKSourceBuilder.<SecurityContext>create(
                            URI.create(jwkSetUri).toURL(),
                            resourceRetriever
                    )
                    .cache(true)
                    .rateLimited(true)
                    .build();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid OAuth JWK Set URI", exception);
        }
        DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource)
        );
        jwtProcessor.setJWTClaimsSetVerifier((claims, context) -> { });
        return new NimbusJwtDecoder(jwtProcessor);
    }

    private static RestTemplate redirectSafeRestTemplate() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(requestFactory);
    }

    private static Resource retrieveJwkSet(RestTemplate restTemplate, URI uri) throws IOException {
        try {
            return restTemplate.execute(uri, HttpMethod.GET, null, response -> {
                byte[] body = response.getBody().readNBytes(MAX_JWK_SET_BYTES + 1);
                if (body.length > MAX_JWK_SET_BYTES) {
                    throw new IOException("OAuth JWK Set response exceeded size limit");
                }
                String contentType = response.getHeaders().getContentType() == null
                        ? null
                        : response.getHeaders().getContentType().toString();
                return new Resource(new String(body, StandardCharsets.UTF_8), contentType);
            });
        } catch (RestClientException exception) {
            throw new IOException("Unable to retrieve OAuth JWK Set", exception);
        }
    }
}
