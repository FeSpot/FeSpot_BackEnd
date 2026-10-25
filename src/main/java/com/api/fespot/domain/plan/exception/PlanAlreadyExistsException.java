package com.api.fespot.domain.plan.exception;

import com.api.fespot.global.exception.BaseException;

public class PlanAlreadyExistsException extends BaseException {
    public PlanAlreadyExistsException(Throwable cause) {
        super(PlanErrorCode.PLAN_CONFLICT_409, cause);
    }
}
