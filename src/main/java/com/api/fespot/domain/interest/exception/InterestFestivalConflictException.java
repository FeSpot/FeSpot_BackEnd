package com.api.fespot.domain.interest.exception;

import com.api.fespot.global.exception.BaseException;

public class InterestFestivalConflictException extends BaseException {
    public InterestFestivalConflictException(Throwable cause) {
        super(InterestErrorCode.INTEREST_CONFLICT_409, cause);
    }
}
