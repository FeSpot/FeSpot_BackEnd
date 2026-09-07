package com.api.fespot.domain.report.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.fespot.domain.report.repository.ReportLikeRepository;
import com.api.fespot.domain.report.repository.ReportRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ReportCleanupServiceTest {

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final ReportLikeRepository reportLikeRepository =
            mock(ReportLikeRepository.class);
    private final ReportCleanupService reportCleanupService =
            new ReportCleanupService(reportRepository, reportLikeRepository);

    // 좋아요가 제보를 참조하므로 좋아요 삭제가 먼저 실행되어야 한다.
    @Test
    void deletesLikesBeforeReports() {
        List<Long> reportIds = List.of(1L, 2L);

        reportCleanupService.deleteReports(reportIds);

        InOrder inOrder = inOrder(reportLikeRepository, reportRepository);
        inOrder.verify(reportLikeRepository).deleteAllByReportIdIn(reportIds);
        inOrder.verify(reportRepository).deleteAllByIdInBatch(reportIds);
    }

    @Test
    void doesNothingWhenReportIdsIsEmpty() {
        reportCleanupService.deleteReports(List.of());

        verifyNoInteractions(reportLikeRepository, reportRepository);
    }
}
