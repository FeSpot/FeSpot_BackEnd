package com.api.fespot.domain.report.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.fespot.domain.report.repository.ReportLikeRepository;
import com.api.fespot.domain.report.repository.ReportLocationRepository;
import com.api.fespot.domain.report.repository.ReportRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ReportCleanupServiceTest {

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final ReportLikeRepository reportLikeRepository =
            mock(ReportLikeRepository.class);
    private final ReportLocationRepository reportLocationRepository =
            mock(ReportLocationRepository.class);
    private final ReportEraser reportEraser =
            new ReportEraser(reportRepository, reportLikeRepository, reportLocationRepository);

    // 좋아요 -> 제보 -> 대표 위치 순서로 FK 참조의 역순으로 삭제되어야 한다.
    @Test
    void deletesLikesBeforeReportsBeforeLocations() {
        List<Long> reportLocationIds = List.of(1L, 2L);

        reportEraser.deleteReports(reportLocationIds);

        InOrder inOrder = inOrder(reportLikeRepository, reportRepository, reportLocationRepository);
        inOrder.verify(reportLikeRepository).deleteAllByReportLocationIdIn(reportLocationIds);
        inOrder.verify(reportRepository).deleteAllByReportLocationIdIn(reportLocationIds);
        inOrder.verify(reportLocationRepository).deleteAllByIdInBatch(reportLocationIds);
    }

    @Test
    void doesNothingWhenReportLocationIdsIsEmpty() {
        reportEraser.deleteReports(List.of());

        verifyNoInteractions(reportLikeRepository, reportRepository, reportLocationRepository);
    }
}
