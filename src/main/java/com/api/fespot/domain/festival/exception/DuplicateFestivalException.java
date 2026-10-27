package com.api.fespot.domain.festival.exception;

import com.api.fespot.global.exception.BaseException;

public class DuplicateFestivalException extends BaseException {
    public DuplicateFestivalException(Throwable cause) {

        super(FestivalErrorResponseCode.FESTIVAL_ALREADY_EXIST_409, cause);
    }
}
