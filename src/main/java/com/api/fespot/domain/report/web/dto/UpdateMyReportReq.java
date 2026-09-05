package com.api.fespot.domain.report.web.dto;

import com.api.fespot.domain.report.entity.CongestionLevel;
import jakarta.validation.constraints.Size;

public record UpdateMyReportReq(
        String imageUrl,

        @Size(max = 500, message = "500자까지 작성가능합니다")
        String content,
        CongestionLevel congestionLevel
) {
}
