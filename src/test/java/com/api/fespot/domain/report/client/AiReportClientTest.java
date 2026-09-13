package com.api.fespot.domain.report.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.api.fespot.domain.festival.client.ai.AiApiProperties;
import com.api.fespot.domain.report.client.dto.AiNormalizedLocationRes;
import com.api.fespot.domain.report.exception.ReportErrorResponseCode;
import com.api.fespot.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiReportClientTest {

    private MockRestServiceServer server;
    private AiReportClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = spy(RestClient.builder());
        server = MockRestServiceServer.bindTo(builder).build();
        doReturn(builder).when(builder).requestFactory(any(ClientHttpRequestFactory.class));
        AiApiProperties properties = new AiApiProperties();
        properties.setBaseUrl("https://ai.example.test");
        client = new AiReportClient(builder, properties);
    }

    @Test
    void sendsFestivalIdAndLocationInTheirOwnFields() {
        server.expect(requestTo("https://ai.example.test/api/v1/community/analyze"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"festival_id":"busan_sea_festival","location":"공연장"}
                        """))
                .andRespond(withSuccess("""
                        {
                          "festival_id":"busan_sea_festival",
                          "original_location":"공연장",
                          "normalized_location":"메인무대",
                          "location_matched":true,
                          "confidence":1.0,
                          "match_method":"festival_alias"
                        }
                        """, MediaType.APPLICATION_JSON));

        AiNormalizedLocationRes response = client.getNormalizedLocation(
                "공연장", "busan_sea_festival");

        assertThat(response.normalized_location()).isEqualTo("메인무대");
        assertThat(response.location_matched()).isTrue();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 502})
    void reportsEmptyResponseAndHttpFailureAsLocationApiError(int status) {
        server.expect(requestTo("https://ai.example.test/api/v1/community/analyze"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> client.getNormalizedLocation("공연장", "busan_sea_festival"))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(ReportErrorResponseCode.REPORT_LOCATION_API_ERROR_502));
        server.verify();
    }
}
