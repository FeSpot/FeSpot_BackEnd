package com.api.fespot.domain.user.exception;

import com.api.fespot.domain.user.enums.response.UserErrorCode;
import com.api.fespot.global.exception.BaseException;

public class UserNotFoundException extends BaseException {
    public UserNotFoundException() {
        super(UserErrorCode.NOT_FOUND);
    }
}
