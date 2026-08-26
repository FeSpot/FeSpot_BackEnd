package com.api.fespot.global.oauth.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import java.nio.charset.StandardCharsets;
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

class GoogleAuthApiClientTest {

    private static final String CLIENT_ID = "google-client-id";
    private static final String CLIENT_SECRET = "google-client-secret";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String REVOKE_URI = "https://oauth2.googleapis.com/revoke";
    private static final String PROVIDER_USER_ID = "google-sub";

    private MockRestServiceServer server;
    private GoogleAuthApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleAuthApiClient(
                builder.build(), CLIENT_ID, CLIENT_SECRET, TOKEN_URI, REVOKE_URI);
    }

    @Test
    void identifiesGoogleProvider() {
        assertThat(client.provider()).isEqualTo(OAuthProvider.GOOGLE);
    }

    @Test
    void exchangesServerAuthCodeForRefreshToken() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(Matchers.containsString("grant_type=authorization_code")))
                .andExpect(content().string(Matchers.containsString("client_id=" + CLIENT_ID)))
                .andExpect(content().string(Matchers.containsString("client_secret=" + CLIENT_SECRET)))
                .andRespond(withSuccess(tokenResponse("google-refresh-token", PROVIDER_USER_ID),
                        MediaType.APPLICATION_JSON));

        Optional<String> refreshToken =
                client.exchangeCodeForRefreshToken("server-auth-code", PROVIDER_USER_ID);

        assertThat(refreshToken).contains("google-refresh-token");
        server.verify();
    }

    @Test
    void rejectsRefreshTokenWhenIdTokenSubjectMismatches() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(tokenResponse("google-refresh-token", "another-sub"),
                        MediaType.APPLICATION_JSON));

        assertThat(client.exchangeCodeForRefreshToken("server-auth-code", PROVIDER_USER_ID)).isEmpty();
    }

    @Test
    void returnsEmptyWhenResponseHasNoRefreshToken() {
        // access_type=offline 미요청 등으로 refresh_token이 누락된 응답
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(
                        "{\"access_token\":\"at\",\"id_token\":\"x.y.z\"}", MediaType.APPLICATION_JSON));

        assertThat(client.exchangeCodeForRefreshToken("server-auth-code", PROVIDER_USER_ID)).isEmpty();
    }

    @Test
    void returnsEmptyWhenTokenEndpointRejectsCode() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.exchangeCodeForRefreshToken("expired-code", PROVIDER_USER_ID)).isEmpty();
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
                .andExpect(content().string(Matchers.containsString("token=google-refresh-token")))
                .andRespond(withSuccess());

        assertThat(client.unlink(PROVIDER_USER_ID, "google-refresh-token")).isTrue();
        server.verify();
    }

    @Test
    void reportsRevokeFailure() {
        server.expect(requestTo(REVOKE_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.unlink(PROVIDER_USER_ID, "google-refresh-token")).isFalse();
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
        return "{\"access_token\":\"at\",\"token_type\":\"Bearer\",\"expires_in\":3599,"
                + "\"refresh_token\":\"" + refreshToken + "\",\"id_token\":\"" + idToken + "\"}";
    }
}
