package com.api.fespot.global.push.client.dto;

import com.api.fespot.global.push.client.ExpoPushErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpoPushTicket(
        String status,

        @JsonProperty("id")
        String receiptId,

        ExpoPushErrorDetails details
) {

    public boolean isAccepted() {
        return "ok".equals(status);
    }

    public boolean isDeviceNotRegistered(){
        return details != null && details.errorCode() == ExpoPushErrorCode.DEVICE_NOT_REGISTERED;
    }
}
