package com.api.fespot.domain.festival.exception;

import com.api.fespot.global.exception.BaseException;

public class DuplicateFestivalLikeException extends BaseException {
    public DuplicateFestivalLikeException(Throwable cause) {
        super(FestivalErrorResponseCode.FESTIVAL_LIKE_ALREADY_EXIST_409, cause);
    }
}
