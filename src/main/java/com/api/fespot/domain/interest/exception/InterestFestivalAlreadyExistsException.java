package com.api.fespot.domain.interest.exception;

import com.api.fespot.global.exception.BaseException;

public class InterestFestivalAlreadyExistsException extends BaseException {
    public InterestFestivalAlreadyExistsException(Throwable cause) {
        super(InterestErrorCode.INTEREST_CONFLICT_409, cause);
    }
}
