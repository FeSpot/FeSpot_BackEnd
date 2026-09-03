package com.api.fespot.global.push.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpoPushReceiptResponse(
        Map<String, ExpoPushReceipt> data
) {
}
