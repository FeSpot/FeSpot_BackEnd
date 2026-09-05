package com.api.fespot.domain.report.web.dto;

import com.api.fespot.domain.report.entity.CongestionLevel;
import com.api.fespot.domain.report.entity.Report;

import java.time.LocalDateTime;

public record MyReportRes(
        Long reportId,
        String imageUrl,
        CongestionLevel congestionLevel,
        String description,
        LocalDateTime createdAt,
        boolean expired
) {
    public static MyReportRes from(Report report, LocalDateTime activeSince) {
        return new MyReportRes(
                report.getId(),
                report.getImageUrl(),
                report.getCongestionLevel(),
                report.getContent(),
                report.getCreatedAt(),
                report.getCreatedAt().isBefore(activeSince)
        );
    }

}
