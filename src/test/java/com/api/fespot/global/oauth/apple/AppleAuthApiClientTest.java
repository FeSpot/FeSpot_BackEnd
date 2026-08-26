package com.api.fespot.global.oauth.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AppleAuthApiClientTest {

    private static final String CLIENT_ID = "com.example.fespot";
    private static final String TOKEN_URI = "https://appleid.apple.com/auth/token";
    private static final String REVOKE_URI = "https://appleid.apple.com/auth/revoke";
    private static final String PROVIDER_USER_ID = "apple-sub";

    private MockRestServiceServer server;
    private AppleAuthApiClient client;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        String privateKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AppleAuthApiClient(
                builder.build(),
                CLIENT_ID,
                AppleClientSecretFactory.create("TEAM123456", CLIENT_ID, "KEY1234567", privateKeyPem),
                TOKEN_URI,
                REVOKE_URI
        );
    }

    @Test
    void exchangesAuthorizationCodeForRefreshToken() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(Matchers.containsString("grant_type=authorization_code")))
                .andExpect(content().string(Matchers.containsString("client_id=" + CLIENT_ID)))
                .andRespond(withSuccess(tokenResponse("apple-refresh-token", PROVIDER_USER_ID),
                        MediaType.APPLICATION_JSON));

        Optional<String> refreshToken =
                client.exchangeCodeForRefreshToken("auth-code", PROVIDER_USER_ID);

        assertThat(refreshToken).contains("apple-refresh-token");
        server.verify();
    }

    @Test
    void rejectsRefreshTokenWhenIdTokenSubjectMismatches() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(tokenResponse("apple-refresh-token", "another-sub"),
                        MediaType.APPLICATION_JSON));

        Optional<String> refreshToken =
                client.exchangeCodeForRefreshToken("auth-code", PROVIDER_USER_ID);

        assertThat(refreshToken).isEmpty();
    }

    @Test
    void returnsEmptyWhenTokenEndpointRejectsCode() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        Optional<String> refreshToken =
                client.exchangeCodeForRefreshToken("expired-code", PROVIDER_USER_ID);

        assertThat(refreshToken).isEmpty();
    }

    @Test
    void returnsEmptyForBlankOrOversizedAuthorizationCode() {
        assertThat(client.exchangeCodeForRefreshToken(" ", PROVIDER_USER_ID)).isEmpty();
        assertThat(client.exchangeCodeForRefreshToken("a".repeat(3_000), PROVIDER_USER_ID)).isEmpty();
    }

    @Test
    void unlinkRevokesStoredRefreshToken() {
        server.expect(requestTo(REVOKE_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(Matchers.containsString("token=apple-refresh-token")))
                .andExpect(content().string(Matchers.containsString("token_type_hint=refresh_token")))
                .andRespond(withSuccess());

        assertThat(client.unlink(PROVIDER_USER_ID, "apple-refresh-token")).isTrue();
        server.verify();
    }

    @Test
    void reportsRevokeFailure() {
        server.expect(requestTo(REVOKE_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.unlink(PROVIDER_USER_ID, "apple-refresh-token")).isFalse();
    }

    @Test
    void skipsRevokeWithoutStoredRefreshToken() {
        assertThat(client.unlink(PROVIDER_USER_ID, null)).isFalse();
        assertThat(client.unlink(PROVIDER_USER_ID, " ")).isFalse();
    }

    private static String tokenResponse(String refreshToken, String subject) {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + subject + "\"}").getBytes(StandardCharsets.UTF_8));
        String idToken = "header." + payload + ".signature";
        return "{\"access_token\":\"at\",\"token_type\":\"Bearer\",\"expires_in\":3600,"
                + "\"refresh_token\":\"" + refreshToken + "\",\"id_token\":\"" + idToken + "\"}";
    }
}
