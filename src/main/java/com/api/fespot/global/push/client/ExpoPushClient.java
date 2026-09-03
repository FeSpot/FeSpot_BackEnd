package com.api.fespot.global.push.client;

import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.push.client.dto.ExpoPushMessage;
import com.api.fespot.global.push.client.dto.ExpoPushReceipt;
import com.api.fespot.global.push.client.dto.ExpoPushReceiptResponse;
import com.api.fespot.global.push.client.dto.ExpoPushTicket;
import com.api.fespot.global.push.client.dto.ExpoPushTicketResponse;
import com.api.fespot.global.push.exception.PushErrorResponseCode;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ExpoPushClient {

    private static final String SEND_PATH = "/--/api/v2/push/send";
    private static final String RECEIPTS_PATH = "/--/api/v2/push/getReceipts";

    private static final int MAX_MESSAGE_COUNT = 100;
    private static final int MAX_RECEIPT_COUNT = 1_000;

    private final RestClient restClient;

    public ExpoPushClient(
            RestClient.Builder restClientBuilder,
            ExpoPushProperties properties
    ) {
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.getConnectTimeout())
                .withReadTimeout(properties.getReadTimeout());

        var requestFactory =
                ClientHttpRequestFactoryBuilder.detect().build(settings);

        RestClient.Builder builder = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                );

        // Expo Push Security를 사용하는 경우에만 필요
        if (StringUtils.hasText(properties.getAccessToken())) {
            builder.defaultHeaders(headers ->
                    headers.setBearerAuth(properties.getAccessToken()));
        }

        this.restClient = builder.build();
    }

    public List<ExpoPushTicket> send(List<ExpoPushMessage> messages) {
        validateMessages(messages);

        List<ExpoPushMessage> request = List.copyOf(messages);

        try {
            ExpoPushTicketResponse response = restClient.post()
                    .uri(SEND_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ExpoPushTicketResponse.class);

            if (response == null
                    || response.data() == null
                    || response.data().size() != request.size()) {
                throw expoApiException();
            }

            return response.data();
        } catch (RestClientException exception) {
            throw expoApiException();
        }
    }

    public Map<String, ExpoPushReceipt> getReceipts(
            List<String> receiptIds
    ) {
        validateReceiptIds(receiptIds);

        List<String> ids = List.copyOf(receiptIds);

        try {
            ExpoPushReceiptResponse response = restClient.post()
                    .uri(RECEIPTS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("ids", ids))
                    .retrieve()
                    .body(ExpoPushReceiptResponse.class);

            if (response == null || response.data() == null) {
                throw expoApiException();
            }

            return response.data();
        } catch (RestClientException exception) {
            throw expoApiException();
        }
    }

    private void validateMessages(List<ExpoPushMessage> messages) {
        if (messages == null
                || messages.isEmpty()
                || messages.size() > MAX_MESSAGE_COUNT
                || messages.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Expo push message는 1개 이상 100개 이하여야 합니다."
            );
        }
    }

    private void validateReceiptIds(List<String> receiptIds) {
        if (receiptIds == null
                || receiptIds.isEmpty()
                || receiptIds.size() > MAX_RECEIPT_COUNT
                || receiptIds.stream().anyMatch(
                id -> !StringUtils.hasText(id))) {
            throw new IllegalArgumentException(
                    "Expo receipt ID는 1개 이상 1000개 이하여야 합니다."
            );
        }
    }

    private CustomException expoApiException() {
        return new CustomException(
                PushErrorResponseCode.EXPO_PUSH_API_ERROR_502
        );
    }
}