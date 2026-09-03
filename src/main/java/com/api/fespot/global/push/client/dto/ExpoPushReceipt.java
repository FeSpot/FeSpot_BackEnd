package com.api.fespot.global.push.client.dto;

import com.api.fespot.global.push.client.ExpoPushErrorCode;

public record ExpoPushReceipt(
        String status,
        ExpoPushErrorDetails details
) {
    public boolean isAcceptedByProvider(){
        return "ok".equals(status);
    }

    public ExpoPushErrorCode errorCode(){
        return details == null ? ExpoPushErrorCode.UNKNOWN : details.errorCode();
    }

}
