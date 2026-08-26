package com.api.fespot.domain.festival.client.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastReq;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastResponse;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastReq;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastResponse;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiFestivalClientTest {

    private MockRestServiceServer server;
    private AiFestivalClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = spy(RestClient.builder());
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        doReturn(restClientBuilder)
                .when(restClientBuilder)
                .requestFactory(any(ClientHttpRequestFactory.class));

        AiApiProperties properties = new AiApiProperties();
        properties.setBaseUrl("https://ai.example.test");
        client = new AiFestivalClient(restClientBuilder, properties);
    }

    @Test
    void fetchesDailyFestivalCongestionForecast() {
        server.expect(requestTo("https://ai.example.test/predict/date"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "festival_name": "부산바다축제",
                          "date": "2026-08-07",
                          "force_refresh": false
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "festival_id": "busan_sea_festival",
                          "festival_name": "부산바다축제",
                          "date": "2026-08-07",
                          "recommended_arrival": {
                            "time": "16:00",
                            "time_range": "16:00~17:00",
                            "main_event_time": "19:00",
                            "expected_congestion": {
                              "score": 34.3,
                              "level": "보통"
                            },
                            "reason": "추천 이유"
                          },
                          "hourly_congestion": [
                            {
                              "time": "19:00~20:00",
                              "festival_congestion": {
                                "score": 59.0,
                                "level": "보통"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        AiDailyFestivalCongestionForecastResponse response =
                client.fetchDailyFestivalCongestionForecast(
                        new AiDailyFestivalCongestionForecastReq(
                                "부산바다축제",
                                "2026-08-07",
                                false));

        assertThat(response.festivalId())
                .isEqualTo("busan_sea_festival");
        assertThat(response.recommendedArrival().time())
                .isEqualTo("16:00");
        assertThat(response.hourlyCongestion()).hasSize(1);
        server.verify();
    }

    @Test
    void fetchesFestivalAreaCongestionForecast() {
        server.expect(requestTo("https://ai.example.test/predict/time"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "festival_id": "busan_sea_festival",
                          "date": "2026-08-07",
                          "time": "19:00",
                          "force_refresh": false
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "festival_id": "busan_sea_festival",
                          "festival_name": "부산바다축제",
                          "date": "2026-08-07",
                          "time": "19:00",
                          "time_range": "19:00~20:00",
                          "festival_congestion": {
                            "score": 59.0,
                            "level": "보통"
                          },
                          "nearby_1km_summary": {
                            "radius_m": 1000,
                            "place_count": 1
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AiFestivalAreaCongestionForecastResponse response =
                client.fetchFestivalAreaCongestionForecast(
                        new AiFestivalAreaCongestionForecastReq(
                                "busan_sea_festival",
                                "2026-08-07",
                                "19:00",
                                false));

        assertThat(response.timeRange()).isEqualTo("19:00~20:00");
        assertThat(response.festivalCongestion().score())
                .isEqualTo(59.0);
        server.verify();
    }

    @Test
    void convertsAiHttpFailureToCustomException() {
        server.expect(requestTo("https://ai.example.test/predict/date"))
                .andRespond(withServerError());

        assertThatThrownBy(() ->
                client.fetchDailyFestivalCongestionForecast(
                        new AiDailyFestivalCongestionForecastReq(
                                "부산바다축제",
                                "2026-08-07",
                                false)))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(
                                        FestivalErrorResponseCode.AI_API_ERROR_502));
        server.verify();
    }

    @Test
    void rejectsEmptyAiResponse() {
        server.expect(requestTo("https://ai.example.test/predict/time"))
                .andRespond(withSuccess(
                        "",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() ->
                client.fetchFestivalAreaCongestionForecast(
                        new AiFestivalAreaCongestionForecastReq(
                                "busan_sea_festival",
                                "2026-08-07",
                                "19:00",
                                false)))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(
                                        FestivalErrorResponseCode.AI_API_ERROR_502));
        server.verify();
    }
}
