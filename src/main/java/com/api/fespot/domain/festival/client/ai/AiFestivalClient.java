package com.api.fespot.domain.festival.client.ai;

import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastReq;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastResponse;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastReq;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastResponse;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.global.exception.CustomException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AiFestivalClient {

    private static final String DAILY_FORECAST_PATH = "/predict/date";
    private static final String AREA_FORECAST_PATH = "/predict/time";

    private final RestClient restClient;

    public AiFestivalClient(
            RestClient.Builder restClientBuilder,
            AiApiProperties properties
    ) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    public AiDailyFestivalCongestionForecastResponse
            fetchDailyFestivalCongestionForecast(
            AiDailyFestivalCongestionForecastReq request
    ) {
        try {
            AiDailyFestivalCongestionForecastResponse response =
                    restClient.post()
                            .uri(DAILY_FORECAST_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(
                                    AiDailyFestivalCongestionForecastResponse.class);

            if (response == null) {
                throw aiApiException();
            }
            return response;
        } catch (RestClientException exception) {
            throw aiApiException();
        }
    }

    public AiFestivalAreaCongestionForecastResponse
            fetchFestivalAreaCongestionForecast(
            AiFestivalAreaCongestionForecastReq request
    ) {
        try {
            AiFestivalAreaCongestionForecastResponse response =
                    restClient.post()
                            .uri(AREA_FORECAST_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(
                                    AiFestivalAreaCongestionForecastResponse.class);

            if (response == null) {
                throw aiApiException();
            }
            return response;
        } catch (RestClientException exception) {
            throw aiApiException();
        }
    }

    private CustomException aiApiException() {
        return new CustomException(
                FestivalErrorResponseCode.AI_API_ERROR_502);
    }
}
