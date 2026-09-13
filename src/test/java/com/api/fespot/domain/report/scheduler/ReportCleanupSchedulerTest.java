package com.api.fespot.domain.report.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.api.fespot.domain.report.entity.ReportLocation;
import com.api.fespot.domain.report.repository.ReportLocationRepository;
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
    private final ReportLocationRepository reportLocationRepository =
            mock(ReportLocationRepository.class);
    private final ReportEraser reportEraser = mock(ReportEraser.class);
    private final S3DeleteService s3DeleteService = mock(S3DeleteService.class);
    private final ReportCleanupScheduler reportCleanupScheduler =
            new ReportCleanupScheduler(
                    reportRepository,
                    reportLocationRepository,
                    reportEraser,
                    s3DeleteService);

    @Test
    void deletesExpiredLocationsAndTheirImages() {
        ReportLocation withImage = reportLocation(1L);
        ReportLocation withoutImage = reportLocation(2L);
        given(reportLocationRepository.findAllByLastReportedAtBefore(any(LocalDateTime.class)))
                .willReturn(List.of(withImage, withoutImage));
        given(reportRepository.findImageUrlsByReportLocationId(1L))
                .willReturn(List.of(IMAGE_URL));
        given(reportRepository.findImageUrlsByReportLocationId(2L))
                .willReturn(List.of());

        reportCleanupScheduler.deleteExpiredReports();

        verify(s3DeleteService).deleteByFileUrl(IMAGE_URL);
        verifyNoMoreInteractions(s3DeleteService);
        verify(reportEraser).deleteReports(List.of(1L, 2L));
    }

    @Test
    void queriesLocationsLastReportedBeforeThirtyMinutesAgo() {
        given(reportLocationRepository.findAllByLastReportedAtBefore(any(LocalDateTime.class)))
                .willReturn(List.of());

        reportCleanupScheduler.deleteExpiredReports();

        ArgumentCaptor<LocalDateTime> captor =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reportLocationRepository).findAllByLastReportedAtBefore(captor.capture());
        assertThat(captor.getValue()).isBetween(
                LocalDateTime.now().minusMinutes(31),
                LocalDateTime.now().minusMinutes(29));
    }

    @Test
    void doesNothingWhenNoExpiredLocations() {
        given(reportLocationRepository.findAllByLastReportedAtBefore(any(LocalDateTime.class)))
                .willReturn(List.of());

        reportCleanupScheduler.deleteExpiredReports();

        verifyNoInteractions(s3DeleteService, reportEraser);
    }

    // 일시적인 S3 오류는 마커를 남겨 다음 주기에 재시도하고, 나머지 마커 삭제는 계속 진행한다.
    @Test
    void keepsLocationWhenS3DeleteFailsTemporarily() {
        ReportLocation failing = reportLocation(1L);
        ReportLocation succeeding = reportLocation(2L);
        given(reportLocationRepository.findAllByLastReportedAtBefore(any(LocalDateTime.class)))
                .willReturn(List.of(failing, succeeding));
        given(reportRepository.findImageUrlsByReportLocationId(1L))
                .willReturn(List.of(IMAGE_URL));
        given(reportRepository.findImageUrlsByReportLocationId(2L))
                .willReturn(List.of(OTHER_IMAGE_URL));
        willThrow(new CustomException(S3ErrorResponseCode.S3_DELETE_FAILED))
                .given(s3DeleteService).deleteByFileUrl(IMAGE_URL);

        reportCleanupScheduler.deleteExpiredReports();

        verify(s3DeleteService).deleteByFileUrl(OTHER_IMAGE_URL);
        verify(reportEraser).deleteReports(List.of(2L));
    }

    // 잘못된 URL은 재시도해도 삭제될 수 없으므로 건너뛰고, 나머지 이미지 삭제와 마커 삭제는 진행한다.
    @Test
    void deletesLocationWhenImageUrlIsInvalid() {
        String invalidUrl = "https://evil.example.com/report/uuid.jpg";
        ReportLocation location = reportLocation(1L);
        given(reportLocationRepository.findAllByLastReportedAtBefore(any(LocalDateTime.class)))
                .willReturn(List.of(location));
        given(reportRepository.findImageUrlsByReportLocationId(1L))
                .willReturn(List.of(invalidUrl, IMAGE_URL));
        willThrow(new CustomException(S3ErrorResponseCode.INVALID_FILE_URL))
                .given(s3DeleteService).deleteByFileUrl(invalidUrl);

        reportCleanupScheduler.deleteExpiredReports();

        verify(s3DeleteService).deleteByFileUrl(IMAGE_URL);
        verify(reportEraser).deleteReports(List.of(1L));
    }

    private ReportLocation reportLocation(Long id) {
        ReportLocation reportLocation = ReportLocation.create(
                null,
                "메인 무대",
                35.1532,
                129.1187);
        ReflectionTestUtils.setField(reportLocation, "id", id);
        return reportLocation;
    }
}
