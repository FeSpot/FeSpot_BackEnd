package com.api.fespot.domain.festival.client.ai;

import com.api.fespot.domain.festival.client.ai.dto.AiCongestionResponse;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastReq;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastResponse;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastReq;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastResponse;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.global.exception.CustomException;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AiFestivalClient {

    private static final String DAILY_FORECAST_PATH = "/predict/date";
    private static final String AREA_FORECAST_PATH = "/predict/time";

    private final RestClient restClient;

    public AiFestivalClient(
            RestClient.Builder restClientBuilder,
            AiApiProperties properties
    ) {
        ClientHttpRequestFactorySettings settings =
                ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(properties.getConnectTimeout())
                        .withReadTimeout(properties.getReadTimeout());

        var requestFactory =
                ClientHttpRequestFactoryBuilder.detect().build(settings);

        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
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

                validateDailyResponse(response);
                return response;
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().value() == 400) {
                    throw new CustomException(
                            FestivalErrorResponseCode.AI_PREDICTION_RANGE_400);
                }
                throw aiApiException();
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

            validateAreaResponse(response);
            return response;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 400) {
                throw new CustomException(
                        FestivalErrorResponseCode.AI_PREDICTION_RANGE_400);
            }
            throw aiApiException();
        } catch (RestClientException exception) {
            throw aiApiException();
        }
    }

    private void validateDailyResponse(
            AiDailyFestivalCongestionForecastResponse response) {
        if (response == null
                || !StringUtils.hasText(response.festivalId())
                || !StringUtils.hasText(response.festivalName())
                || !StringUtils.hasText(response.date())
                || response.recommendedArrival() == null
                || response.hourlyCongestion() == null
                || response.hourlyCongestion().isEmpty()) {
            throw aiApiException();
        }

        var arrival = response.recommendedArrival();
        if (!StringUtils.hasText(arrival.time())
                || !StringUtils.hasText(arrival.timeRange())
                || !StringUtils.hasText(arrival.mainEventTime())
                || !StringUtils.hasText(arrival.reason())
                || !hasValidCongestion(arrival.expectedCongestion())) {
            throw aiApiException();
        }

        for (var hour : response.hourlyCongestion()) {
            if (hour == null
                    || !StringUtils.hasText(hour.time())
                    || !hasValidCongestion(hour.festivalCongestion())) {
                throw aiApiException();
            }
        }
    }

    private void validateAreaResponse(
            AiFestivalAreaCongestionForecastResponse response) {
        if (response == null
                || !StringUtils.hasText(response.festivalId())
                || !StringUtils.hasText(response.festivalName())
                || !StringUtils.hasText(response.date())
                || !StringUtils.hasText(response.time())
                || !StringUtils.hasText(response.timeRange())
                || response.nearby1kmSummary() == null
                || response.nearby1kmCongestion() == null) {
            throw aiApiException();
        }

        var summary = response.nearby1kmSummary();
        if (summary.radiusM() == null || summary.placeCount() == null) {
            throw aiApiException();
        }

        for (var place : response.nearby1kmCongestion()) {
            if (place == null
                    || !StringUtils.hasText(place.placeName())
                    || !StringUtils.hasText(place.category())
                    || place.distanceM() == null
                    || !StringUtils.hasText(place.level())) {
                throw aiApiException();
            }
        }
    }

    private boolean hasValidCongestion(AiCongestionResponse congestion) {
        return congestion != null
                && congestion.score() != null
                && StringUtils.hasText(congestion.level());
    }

    private CustomException aiApiException() {
        return new CustomException(
                FestivalErrorResponseCode.AI_API_ERROR_502);
    }
}
