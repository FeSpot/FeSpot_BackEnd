package com.api.fespot.domain.report.web.dto;

import com.api.fespot.domain.report.entity.CongestionLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record ReportCreateReq(
        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
        Double latitude,

        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
        Double longitude,

        @NotBlank(message = "제보 내용은 필수입니다.")
        @Size(min = 5, max = 500, message = "제보 내용은 5자 이상 500자 이하여야 합니다.")
        String content,

        @NotNull(message = "혼잡도는 필수입니다.")
        CongestionLevel congestionLevel,

        @URL(protocol = "https" , message = "이미지 url은 올바른 HTTPS URL 이어야 합니다.")
        String imageUrl,

        String signatureName
) {
}
