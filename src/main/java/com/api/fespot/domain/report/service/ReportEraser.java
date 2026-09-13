package com.api.fespot.domain.report.service;

import com.api.fespot.domain.report.repository.ReportLikeRepository;
import com.api.fespot.domain.report.repository.ReportLocationRepository;
import com.api.fespot.domain.report.repository.ReportRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportEraser {

    private final ReportRepository reportRepository;
    private final ReportLikeRepository reportLikeRepository;
    private final ReportLocationRepository reportLocationRepository;

    // 좋아요 ->---+ 제보이므로 좋아요를 먼저 지운 뒤 제보를 일괄 삭제.
    @Transactional
    public void deleteReports(List<Long> reportLocationIds) {
        if (reportLocationIds.isEmpty()) {
            return;
        }
        reportLikeRepository.deleteAllByReportLocationIdIn(reportLocationIds);
        reportRepository.deleteAllByReportLocationIdIn(reportLocationIds);
        reportLocationRepository.deleteAllByIdInBatch(reportLocationIds);
    }
}
