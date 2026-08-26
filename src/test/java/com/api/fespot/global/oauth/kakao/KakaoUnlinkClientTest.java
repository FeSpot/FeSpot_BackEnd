package com.api.fespot.global.oauth.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoUnlinkClientTest {

    private static final String ADMIN_KEY = "kakao-admin-key";
    private static final String UNLINK_URI = "https://kapi.kakao.com/v1/user/unlink";

    private MockRestServiceServer server;
    private KakaoUnlinkClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoUnlinkClient(builder.build(), ADMIN_KEY, UNLINK_URI);
    }

    @Test
    void identifiesKakaoProvider() {
        assertThat(client.provider()).isEqualTo(OAuthProvider.KAKAO);
    }

    @Test
    void unlinksByAdminKeyAndTargetId() {
        server.expect(requestTo(UNLINK_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "KakaoAK " + ADMIN_KEY))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(Matchers.containsString("target_id_type=user_id")))
                .andExpect(content().string(Matchers.containsString("target_id=12345")))
                .andRespond(withSuccess("{\"id\":12345}", MediaType.APPLICATION_JSON));

        assertThat(client.unlink("12345", null)).isTrue();
        server.verify();
    }

    @Test
    void reportsUnlinkFailure() {
        server.expect(requestTo(UNLINK_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.unlink("12345", null)).isFalse();
    }

    @Test
    void rejectsNonNumericProviderUserIdWithoutApiCall() {
        assertThat(client.unlink("not-a-number", null)).isFalse();
        assertThat(client.unlink("0123", null)).isFalse();
        assertThat(client.unlink(null, null)).isFalse();
        server.verify();
    }
}
