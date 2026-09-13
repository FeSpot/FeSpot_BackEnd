package com.api.fespot.domain.report.client;

import com.api.fespot.domain.festival.client.ai.AiApiProperties;
import com.api.fespot.domain.report.exception.ReportErrorResponseCode;
import com.api.fespot.domain.report.client.dto.AiNormalizedLocationReq;
import com.api.fespot.domain.report.client.dto.AiNormalizedLocationRes;
import com.api.fespot.global.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class AiReportClient {
    private static final String NORMALIZE_LOCATION_API_PATH = "/api/v1/community/analyze";
    private final RestClient restClient;

    public AiReportClient(
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

    public AiNormalizedLocationRes getNormalizedLocation(String location, String festivalId) {
        AiNormalizedLocationReq request = new AiNormalizedLocationReq(festivalId, location);
        try{
            AiNormalizedLocationRes response = restClient
                    .post()
                    .uri(NORMALIZE_LOCATION_API_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiNormalizedLocationRes.class);

            if(response == null){
                log.error("AI 대표 장소명 응답이 비어 있음: festivalId={}", festivalId);
                throw aiApiException();
            }
            return response;

        } catch (RestClientException exception) {
            log.error("AI 대표 장소명 조회 실패: festivalId={}", festivalId, exception);
            throw aiApiException();
        }
    }

    private CustomException aiApiException() {
        return new CustomException(
                ReportErrorResponseCode.REPORT_LOCATION_API_ERROR_502);
    }
}
