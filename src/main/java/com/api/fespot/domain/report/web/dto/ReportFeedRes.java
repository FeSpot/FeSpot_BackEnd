package com.api.fespot.domain.report.web.dto;

import com.api.fespot.domain.report.entity.CongestionLevel;
import com.api.fespot.domain.report.entity.Report;

import java.time.LocalDateTime;

public record ReportFeedRes(
        Long reportId,
        String imageUrl,
        CongestionLevel congestionLevel,
        String description,
        long likeCount,
        boolean likedByMe,
        LocalDateTime createdAt,
        boolean expired
) {
    public static ReportFeedRes from(Report report, long likeCount, boolean likedByMe, LocalDateTime activeSince) {
        return new ReportFeedRes(
                report.getId(),
                report.getImageUrl(),
                report.getCongestionLevel(),
                report.getContent(),
                likeCount,
                likedByMe,
                report.getCreatedAt(),
                report.getCreatedAt().isBefore(activeSince)
        );
    }
}
