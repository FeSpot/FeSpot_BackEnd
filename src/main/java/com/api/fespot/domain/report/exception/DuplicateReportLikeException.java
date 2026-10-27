package com.api.fespot.domain.report.exception;

import com.api.fespot.global.exception.BaseException;

public class DuplicateReportLikeException extends BaseException {
    public DuplicateReportLikeException(Throwable cause) {
        super(ReportErrorResponseCode.REPORT_LIKE_ALREADY_EXIST, cause);
    }
}
