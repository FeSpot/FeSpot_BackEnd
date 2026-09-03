package com.api.fespot.global.push.client.dto;

import com.api.fespot.global.push.client.ExpoPushErrorCode;

public record ExpoPushErrorDetails(
        String error
) {
    public ExpoPushErrorCode errorCode() {
        return ExpoPushErrorCode.from(error);
    }
}
