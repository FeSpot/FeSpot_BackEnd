package com.api.fespot.domain.interest.exception;

import com.api.fespot.domain.interest.enums.InterestFestivalErrorResponseCode;
import com.api.fespot.global.exception.BaseException;

public class InterestFestivalAlreadyExistsException extends BaseException {
    public InterestFestivalAlreadyExistsException(Throwable cause) {
        super(InterestFestivalErrorResponseCode.INTEREST_FESTIVAL_ALREADY_EXISTS, cause);
    }
}
