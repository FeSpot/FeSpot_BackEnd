package com.api.fespot.domain.festival.exception;

import com.api.fespot.global.exception.BaseException;

public class DuplicateFestivalNotificationException extends BaseException {
    public DuplicateFestivalNotificationException(Throwable cause) {
        super(FestivalErrorResponseCode.FESTIVAL_NOTIFICATION_ALREADY_EXIST_409, cause);
    }
}
