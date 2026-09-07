package com.api.fespot.domain.report.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.api.fespot.domain.report.entity.CongestionLevel;
import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.repository.ReportRepository;
import com.api.fespot.domain.report.service.ReportEraser;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.s3.enums.code.S3ErrorResponseCode;
import com.api.fespot.global.s3.service.S3DeleteService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ReportCleanupSchedulerTest {

    private static final String IMAGE_URL =
            "https://fespot-bucket.s3.ap-northeast-2.amazonaws.com/report/uuid.jpg";
    private static final String OTHER_IMAGE_URL =
            "https://fespot-bucket.s3.ap-northeast-2.amazonaws.com/report/other.jpg";

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final ReportEraser reportEraser =
            mock(ReportEraser.class);
    private final S3DeleteService s3DeleteService = mock(S3DeleteService.class);
    private final ReportCleanupScheduler reportCleanupScheduler =
            new ReportCleanupScheduler(
                    reportRepository,
                    reportEraser,
                    s3DeleteService);

    @Test
    void deletesExpiredReportsAndTheirImages() {
        Report withImage = report(1L, IMAGE_URL);
        Report withoutImage = report(2L, null);
        given(reportRepository.findAllByCreatedAtBefore(any(LocalDateTime.class)))
                .willReturn(List.of(withImage, withoutImage));

        reportCleanupScheduler.deleteExpiredReports();

        verify(s3DeleteService).deleteByFileUrl(IMAGE_URL);
        verifyNoMoreInteractions(s3DeleteService);
        verify(reportEraser).deleteReports(List.of(1L, 2L));
    }

    @Test
    void queriesReportsCreatedBeforeThirtyMinutesAgo() {
        given(reportRepository.findAllByCreatedAtBefore(any(LocalDateTime.class)))
                .willReturn(List.of());

        reportCleanupScheduler.deleteExpiredReports();

        ArgumentCaptor<LocalDateTime> captor =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reportRepository).findAllByCreatedAtBefore(captor.capture());
        assertThat(captor.getValue()).isBetween(
                LocalDateTime.now().minusMinutes(31),
                LocalDateTime.now().minusMinutes(29));
    }

    @Test
    void doesNothingWhenNoExpiredReports() {
        given(reportRepository.findAllByCreatedAtBefore(any(LocalDateTime.class)))
                .willReturn(List.of());

        reportCleanupScheduler.deleteExpiredReports();

        verifyNoInteractions(s3DeleteService, reportEraser);
    }

    // 일시적인 S3 오류는 제보를 남겨 다음 주기에 재시도하고, 나머지 제보 삭제는 계속 진행한다.
    @Test
    void keepsReportWhenS3DeleteFailsTemporarily() {
        Report failing = report(1L, IMAGE_URL);
        Report succeeding = report(2L, OTHER_IMAGE_URL);
        given(reportRepository.findAllByCreatedAtBefore(any(LocalDateTime.class)))
                .willReturn(List.of(failing, succeeding));
        willThrow(new CustomException(S3ErrorResponseCode.S3_DELETE_FAILED))
                .given(s3DeleteService).deleteByFileUrl(IMAGE_URL);

        reportCleanupScheduler.deleteExpiredReports();

        verify(s3DeleteService).deleteByFileUrl(OTHER_IMAGE_URL);
        verify(reportEraser).deleteReports(List.of(2L));
    }

    // 잘못된 URL은 재시도해도 삭제될 수 없으므로 제보는 삭제한다.
    @Test
    void deletesReportWhenImageUrlIsInvalid() {
        Report invalidUrl = report(1L, "https://evil.example.com/report/uuid.jpg");
        given(reportRepository.findAllByCreatedAtBefore(any(LocalDateTime.class)))
                .willReturn(List.of(invalidUrl));
        willThrow(new CustomException(S3ErrorResponseCode.INVALID_FILE_URL))
                .given(s3DeleteService).deleteByFileUrl(invalidUrl.getImageUrl());

        reportCleanupScheduler.deleteExpiredReports();

        verify(reportEraser).deleteReports(List.of(1L));
    }

    @Test
    void skipsS3DeleteWhenImageUrlIsBlank() {
        Report blankImage = report(1L, " ");
        given(reportRepository.findAllByCreatedAtBefore(any(LocalDateTime.class)))
                .willReturn(List.of(blankImage));

        reportCleanupScheduler.deleteExpiredReports();

        verifyNoInteractions(s3DeleteService);
        verify(reportEraser).deleteReports(List.of(1L));
    }

    private Report report(Long id, String imageUrl) {
        Report report = Report.builder()
                .content("제보 내용")
                .congestionLevel(CongestionLevel.NORMAL)
                .imageUrl(imageUrl)
                .build();
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }
}
