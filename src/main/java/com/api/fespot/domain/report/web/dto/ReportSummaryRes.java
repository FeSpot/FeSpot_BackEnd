package com.api.fespot.domain.report.web.dto;

import com.api.fespot.domain.report.entity.CongestionLevel;

import java.time.LocalDateTime;

public record ReportSummaryRes(
        Long reportLocationId,
        String signatureName,
        int reportCount,
        CongestionLevel representativeLevel,
        LocalDateTime latestReportedAt
) {
    public static ReportSummaryRes of(Long reportLocationId, String signatureName, int reportCount, CongestionLevel representativeLevel, LocalDateTime latestReportedAt) {
        return new ReportSummaryRes(reportLocationId, signatureName, reportCount, representativeLevel, latestReportedAt);
    }
}
