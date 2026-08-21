package com.api.fespot.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.report.entity.CongestionLevel;
import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.entity.ReportLocation;
import com.api.fespot.domain.report.repository.ReportLocationRepository;
import com.api.fespot.domain.report.repository.ReportRepository;
import com.api.fespot.domain.report.web.dto.ReportCreateReq;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.response.code.ErrorResponseCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReportServiceTest {

    private static final Long USER_ID = 1L;
    private static final String CONTENT_ID = "123456";
    private static final int YEAR = 2026;
    private static final double LATITUDE = 35.1532;
    private static final double LONGITUDE = 129.1187;
    private static final String IMAGE_URL = "https://example.com/report.jpg";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final FestivalJpaRepository festivalJpaRepository =
            mock(FestivalJpaRepository.class);
    private final ReportLocationRepository reportLocationRepository =
            mock(ReportLocationRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final ReportService reportService = new ReportService(
            userRepository,
            festivalJpaRepository,
            reportLocationRepository,
            reportRepository);

    @Test
    void createsLocationAndReportWhenNearbyLocationDoesNotExist() {
        User user = user();
        Festival festival = festival();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(reportLocationRepository.findActiveByFestival(
                eq(festival),
                any(LocalDateTime.class)))
                .willReturn(List.of());
        given(reportLocationRepository.save(any(ReportLocation.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(reportRepository.save(any(Report.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Report report = reportService.createReport(
                USER_ID,
                CONTENT_ID,
                YEAR,
                request(),
                IMAGE_URL);

        ArgumentCaptor<ReportLocation> locationCaptor =
                ArgumentCaptor.forClass(ReportLocation.class);
        verify(reportLocationRepository).save(locationCaptor.capture());
        ReportLocation savedLocation = locationCaptor.getValue();
        assertThat(savedLocation.getFestival()).isSameAs(festival);
        assertThat(savedLocation.getAnchorLatitude()).isEqualTo(LATITUDE);
        assertThat(savedLocation.getAnchorLongitude()).isEqualTo(LONGITUDE);
        assertThat(report.getUser()).isSameAs(user);
        assertThat(report.getReportLocation()).isSameAs(savedLocation);
        assertThat(report.getImageUrl()).isEqualTo(IMAGE_URL);
    }

    @Test
    void reusesNearestLocationWithinTwentyFiveMeters() {
        User user = user();
        Festival festival = festival();
        ReportLocation nearest = ReportLocation.create(
                festival,
                LATITUDE + 0.00005,
                LONGITUDE);
        ReportLocation farther = ReportLocation.create(
                festival,
                LATITUDE + 0.00015,
                LONGITUDE);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(reportLocationRepository.findActiveByFestival(
                eq(festival),
                any(LocalDateTime.class)))
                .willReturn(List.of(farther, nearest));
        given(reportRepository.save(any(Report.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Report report = reportService.createReport(
                USER_ID,
                CONTENT_ID,
                YEAR,
                request(),
                null);

        assertThat(report.getReportLocation()).isSameAs(nearest);
        verify(reportLocationRepository, never()).save(any(ReportLocation.class));
    }

    @Test
    void createsLocationWhenEveryExistingLocationIsOutsideTwentyFiveMeters() {
        Festival festival = festival();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(reportLocationRepository.findActiveByFestival(
                eq(festival),
                any(LocalDateTime.class)))
                .willReturn(List.of(ReportLocation.create(
                        festival,
                        LATITUDE + 0.001,
                        LONGITUDE)));
        given(reportLocationRepository.save(any(ReportLocation.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(reportRepository.save(any(Report.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        reportService.createReport(
                USER_ID,
                CONTENT_ID,
                YEAR,
                request(),
                null);

        verify(reportLocationRepository).save(any(ReportLocation.class));
    }

    @Test
    void rejectsTokenUserThatNoLongerExists() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.createReport(
                USER_ID,
                CONTENT_ID,
                YEAR,
                request(),
                null))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(ErrorResponseCode.UNAUTHORIZED));

        verifyNoInteractions(
                festivalJpaRepository,
                reportLocationRepository,
                reportRepository);
    }

    @Test
    void rejectsFestivalThatIsNotRegistered() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.createReport(
                USER_ID,
                CONTENT_ID,
                YEAR,
                request(),
                null))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(
                                        FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));

        verifyNoInteractions(reportLocationRepository, reportRepository);
    }

    private ReportCreateReq request() {
        return new ReportCreateReq(
                LATITUDE,
                LONGITUDE,
                "메인 무대 앞에 사람이 많아요.",
                CongestionLevel.CROWDED);
    }

    private User user() {
        return User.create("user@example.com", "사용자");
    }

    private Festival festival() {
        return Festival.create(CONTENT_ID, YEAR);
    }
}
