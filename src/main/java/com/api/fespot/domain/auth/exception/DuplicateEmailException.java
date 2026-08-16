package com.api.fespot.domain.auth.exception;

import com.api.fespot.global.exception.BaseException;

public class DuplicateEmailException extends BaseException {

    public DuplicateEmailException() {
        super(AuthErrorResponseCode.DUPLICATED_EMAIL_409);
    }
}
