package com.api.fespot.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.report.entity.CongestionLevel;
import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.entity.ReportLocation;
import com.api.fespot.domain.report.exception.ReportErrorResponseCode;
import com.api.fespot.domain.report.repository.ReportLikeCount;
import com.api.fespot.domain.report.repository.ReportLikeRepository;
import com.api.fespot.domain.report.repository.ReportLocationRepository;
import com.api.fespot.domain.report.repository.ReportRepository;
import com.api.fespot.domain.report.web.dto.MyReportRes;
import com.api.fespot.domain.report.web.dto.ReportCreateReq;
import com.api.fespot.domain.report.web.dto.ReportFeedRes;
import com.api.fespot.domain.report.web.dto.ReportSummaryRes;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.response.CursorPage;
import com.api.fespot.global.response.code.ErrorResponseCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

class ReportServiceTest {

    private static final Long USER_ID = 1L;
    private static final String CONTENT_ID = "123456";
    private static final int YEAR = 2026;
    private static final double LATITUDE = 35.1532;
    private static final double LONGITUDE = 129.1187;
    private static final String IMAGE_URL = "https://example.com/report.jpg";
    private static final String SIGNATURE_NAME = "메인 무대";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final FestivalJpaRepository festivalJpaRepository =
            mock(FestivalJpaRepository.class);
    private final ReportLocationRepository reportLocationRepository =
            mock(ReportLocationRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final ReportLikeRepository reportLikeRepository =
            mock(ReportLikeRepository.class);
    private final ReportService reportService = new ReportService(
            userRepository,
            festivalJpaRepository,
            reportLocationRepository,
            reportRepository,
            reportLikeRepository);

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
                request());

        ArgumentCaptor<ReportLocation> locationCaptor =
                ArgumentCaptor.forClass(ReportLocation.class);
        verify(reportLocationRepository).save(locationCaptor.capture());
        ReportLocation savedLocation = locationCaptor.getValue();
        assertThat(savedLocation.getFestival()).isSameAs(festival);
        assertThat(savedLocation.getSignatureName()).isEqualTo(SIGNATURE_NAME);
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
                SIGNATURE_NAME,
                LATITUDE + 0.00005,
                LONGITUDE);
        ReportLocation farther = ReportLocation.create(
                festival,
                SIGNATURE_NAME,
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
                request());

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
                        SIGNATURE_NAME,
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
                request());

        verify(reportLocationRepository).save(any(ReportLocation.class));
    }

    @Test
    void rejectsTokenUserThatNoLongerExists() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.createReport(
                USER_ID,
                CONTENT_ID,
                YEAR,
                request()))
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
                request()))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(
                                        FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));

        verifyNoInteractions(reportLocationRepository, reportRepository);
    }

    @Test
    void returnsRequestedMyReportsAndNextCursorWhenNextPageExists() {
        User user = user();
        Festival festival = festival();
        Long cursor = 50L;
        int size = 2;
        LocalDateTime createdAt = LocalDateTime.now();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(reportRepository.findCursorPageByUser(
                user,
                cursor,
                Limit.of(size + 1)))
                .willReturn(List.of(
                        report(festival, 40L, createdAt),
                        report(festival, 30L, createdAt),
                        report(festival, 20L, createdAt)));

        CursorPage<MyReportRes> page = reportService.getMyReports(
                USER_ID,
                cursor,
                size);

        assertThat(page.getItems())
                .extracting(MyReportRes::reportId)
                .containsExactly(40L, 30L);
        assertThat(page.getPageInfo().hasNext()).isTrue();
        assertThat(page.getPageInfo().nextCursor()).isEqualTo("30");
        verify(reportRepository).findCursorPageByUser(
                user,
                cursor,
                Limit.of(size + 1));
    }

    @Test
    void returnsLastMyReportsPageAndMarksExpiredReports() {
        User user = user();
        Festival festival = festival();
        int size = 2;
        LocalDateTime now = LocalDateTime.now();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(reportRepository.findCursorPageByUser(
                user,
                null,
                Limit.of(size + 1)))
                .willReturn(List.of(
                        report(festival, 20L, now.minusMinutes(29)),
                        report(festival, 10L, now.minusMinutes(31))));

        CursorPage<MyReportRes> page = reportService.getMyReports(
                USER_ID,
                null,
                size);

        assertThat(page.getItems())
                .extracting(MyReportRes::reportId)
                .containsExactly(20L, 10L);
        assertThat(page.getItems())
                .extracting(MyReportRes::expired)
                .containsExactly(false, true);
        assertThat(page.getPageInfo().hasNext()).isFalse();
        assertThat(page.getPageInfo().nextCursor()).isNull();
    }

    @Test
    void returnsEmptyMyReportsPageWhenUserHasNoReports() {
        User user = user();
        int size = 3;
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(reportRepository.findCursorPageByUser(
                user,
                null,
                Limit.of(size + 1)))
                .willReturn(List.of());

        CursorPage<MyReportRes> page = reportService.getMyReports(
                USER_ID,
                null,
                size);

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getPageInfo().hasNext()).isFalse();
        assertThat(page.getPageInfo().nextCursor()).isNull();
    }

    @Test
    void rejectsMyReportsWhenTokenUserNoLongerExists() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getMyReports(USER_ID, null, 10))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(ErrorResponseCode.UNAUTHORIZED));

        verifyNoInteractions(reportRepository);
    }

    @Test
    void deletesMyReportOwnedByUser() {
        User user = user();
        Report report = mock(Report.class);
        Long reportId = 10L;
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(reportRepository.findByUserAndId(user, reportId))
                .willReturn(Optional.of(report));

        reportService.deleteMyReport(USER_ID, reportId);

        verify(reportRepository).findByUserAndId(user, reportId);
        InOrder deletionOrder = inOrder(reportLikeRepository, reportRepository);
        deletionOrder.verify(reportLikeRepository).deleteAllByReportId(reportId);
        deletionOrder.verify(reportRepository).delete(report);
    }

    @Test
    void rejectsDeletingMyReportWhenTokenUserNoLongerExists() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.deleteMyReport(USER_ID, 10L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(ErrorResponseCode.UNAUTHORIZED));

        verifyNoInteractions(reportRepository);
    }

    @Test
    void rejectsDeletingReportThatDoesNotBelongToUser() {
        User user = user();
        Long reportId = 10L;
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(reportRepository.findByUserAndId(user, reportId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.deleteMyReport(USER_ID, reportId))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(
                                        ReportErrorResponseCode.REPORT_NOT_FOUND_404));

        verify(reportRepository).findByUserAndId(user, reportId);
        verify(reportRepository, never()).delete(any(Report.class));
    }

    @Test
    void returnsRequestedSizeAndNextCursorWhenNextPageExists() {
        Festival festival = festival();
        Long cursor = 50L;
        int size = 2;
        LocalDateTime createdAt = LocalDateTime.now();
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(reportRepository.findFeedByFestival(
                festival,
                cursor,
                Limit.of(size + 1)))
                .willReturn(List.of(
                        report(festival, 40L, createdAt),
                        report(festival, 30L, createdAt),
                        report(festival, 20L, createdAt)));
        List<Long> reportIds = List.of(40L, 30L);
        given(reportLikeRepository.countByReportIds(reportIds))
                .willReturn(List.of(likeCount(40L, 2L)));
        given(reportLikeRepository.findLikeReportIds(USER_ID, reportIds))
                .willReturn(List.of(30L));

        CursorPage<ReportFeedRes> page = reportService.getReportFeedList(
                USER_ID,
                CONTENT_ID,
                YEAR,
                cursor,
                size);

        assertThat(page.getItems())
                .extracting(ReportFeedRes::reportId)
                .containsExactly(40L, 30L);
        assertThat(page.getItems())
                .extracting(ReportFeedRes::likeCount)
                .containsExactly(2L, 0L);
        assertThat(page.getItems())
                .extracting(ReportFeedRes::likedByMe)
                .containsExactly(false, true);
        assertThat(page.getPageInfo().hasNext()).isTrue();
        assertThat(page.getPageInfo().nextCursor()).isEqualTo("30");
        verify(reportRepository).findFeedByFestival(
                festival,
                cursor,
                Limit.of(size + 1));
        verify(reportLikeRepository).countByReportIds(reportIds);
        verify(reportLikeRepository).findLikeReportIds(USER_ID, reportIds);
    }

    @Test
    void returnsAllReportsWithoutCursorOnLastPage() {
        Festival festival = festival();
        int size = 3;
        LocalDateTime createdAt = LocalDateTime.now();
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(reportRepository.findFeedByFestival(
                festival,
                null,
                Limit.of(size + 1)))
                .willReturn(List.of(
                        report(festival, 20L, createdAt),
                        report(festival, 10L, createdAt)));

        CursorPage<ReportFeedRes> page = reportService.getReportFeedList(
                null,
                CONTENT_ID,
                YEAR,
                null,
                size);

        assertThat(page.getItems())
                .extracting(ReportFeedRes::reportId)
                .containsExactly(20L, 10L);
        assertThat(page.getPageInfo().hasNext()).isFalse();
        assertThat(page.getPageInfo().nextCursor()).isNull();
    }

    @Test
    void returnsEmptyLastPageWhenNoReportsExist() {
        Festival festival = festival();
        int size = 3;
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(reportRepository.findFeedByFestival(
                festival,
                null,
                Limit.of(size + 1)))
                .willReturn(List.of());

        CursorPage<ReportFeedRes> page = reportService.getReportFeedList(
                null,
                CONTENT_ID,
                YEAR,
                null,
                size);

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getPageInfo().hasNext()).isFalse();
        assertThat(page.getPageInfo().nextCursor()).isNull();
        verifyNoInteractions(reportLikeRepository);
    }

    @Test
    void rejectsFeedRequestWhenFestivalIsNotRegistered() {
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getReportFeedList(
                null,
                CONTENT_ID,
                YEAR,
                null,
                10))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(
                                        FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));

        verifyNoInteractions(reportRepository);
    }

    @Test
    void marksReportsOlderThanThirtyMinutesAsExpired() {
        Festival festival = festival();
        LocalDateTime now = LocalDateTime.now();
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(reportRepository.findFeedByFestival(
                festival,
                null,
                Limit.of(3)))
                .willReturn(List.of(
                        report(festival, 20L, now.minusMinutes(31)),
                        report(festival, 10L, now.minusMinutes(29))));

        CursorPage<ReportFeedRes> page = reportService.getReportFeedList(
                null,
                CONTENT_ID,
                YEAR,
                null,
                2);

        assertThat(page.getItems())
                .extracting(ReportFeedRes::expired)
                .containsExactly(true, false);
    }

    @Test
    void summarizesReportsByLocationAndSortsByLatestReport() {
        Festival festival = festival();
        ReportLocation firstLocation = reportLocation(festival, 10L);
        ReportLocation secondLocation = reportLocation(festival, 20L);
        LocalDateTime firstLatestAt = LocalDateTime.of(
                2026, 8, 23, 14, 50);
        LocalDateTime secondLatestAt = LocalDateTime.of(
                2026, 8, 23, 14, 55);
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(reportRepository.findActiveByFestival(
                eq(festival),
                any(LocalDateTime.class)))
                .willReturn(List.of(
                        report(firstLocation, 1L, CongestionLevel.SAFE,
                                firstLatestAt.minusMinutes(2)),
                        report(secondLocation, 2L, CongestionLevel.DANGER,
                                secondLatestAt.minusMinutes(1)),
                        report(firstLocation, 3L, CongestionLevel.CROWDED,
                                firstLatestAt.minusMinutes(1)),
                        report(secondLocation, 4L, CongestionLevel.DANGER,
                                secondLatestAt),
                        report(firstLocation, 5L, CongestionLevel.CROWDED,
                                firstLatestAt)));

        List<ReportSummaryRes> summaries = reportService.getReportSummaryList(
                CONTENT_ID,
                YEAR);

        assertThat(summaries).containsExactly(
                new ReportSummaryRes(
                        20L,
                        2,
                        null,
                        secondLatestAt),
                new ReportSummaryRes(
                        10L,
                        3,
                        CongestionLevel.CROWDED,
                        firstLatestAt));
    }

    @Test
    void selectsHigherCongestionLevelWhenCountsAreTied() {
        Festival festival = festival();
        ReportLocation location = reportLocation(festival, 10L);
        LocalDateTime latestAt = LocalDateTime.of(
                2026, 8, 23, 15, 0);
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(reportRepository.findActiveByFestival(
                eq(festival),
                any(LocalDateTime.class)))
                .willReturn(List.of(
                        report(location, 1L, CongestionLevel.NORMAL,
                                latestAt.minusMinutes(3)),
                        report(location, 2L, CongestionLevel.CROWDED,
                                latestAt.minusMinutes(2)),
                        report(location, 3L, CongestionLevel.NORMAL,
                                latestAt.minusMinutes(1)),
                        report(location, 4L, CongestionLevel.CROWDED,
                                latestAt)));

        List<ReportSummaryRes> summaries = reportService.getReportSummaryList(
                CONTENT_ID,
                YEAR);

        assertThat(summaries).containsExactly(new ReportSummaryRes(
                10L,
                4,
                CongestionLevel.CROWDED,
                latestAt));
    }

    @Test
    void returnsEmptySummaryWhenNoActiveReportsExist() {
        Festival festival = festival();
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(reportRepository.findActiveByFestival(
                eq(festival),
                any(LocalDateTime.class)))
                .willReturn(List.of());

        List<ReportSummaryRes> summaries = reportService.getReportSummaryList(
                CONTENT_ID,
                YEAR);

        assertThat(summaries).isEmpty();
    }

    @Test
    void rejectsSummaryRequestWhenFestivalIsNotRegistered() {
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.getReportSummaryList(
                CONTENT_ID,
                YEAR))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(
                                        FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));

        verifyNoInteractions(reportRepository);
    }

    private ReportCreateReq request() {
        return new ReportCreateReq(
                LATITUDE,
                LONGITUDE,
                "메인 무대 앞에 사람이 많아요.",
                CongestionLevel.CROWDED,
                IMAGE_URL,
                SIGNATURE_NAME);
    }

    private User user() {
        return User.create("user@example.com", "사용자");
    }

    private Festival festival() {
        return Festival.create(CONTENT_ID, YEAR);
    }

    private Report report(Festival festival, Long id, LocalDateTime createdAt) {
        ReportLocation location = ReportLocation.create(
                festival,
                SIGNATURE_NAME,
                LATITUDE,
                LONGITUDE);
        Report report = buildReport(
                user(),
                location,
                "제보 " + id,
                CongestionLevel.CROWDED,
                IMAGE_URL);
        ReflectionTestUtils.setField(report, "id", id);
        ReflectionTestUtils.setField(report, "createdAt", createdAt);
        return report;
    }

    private ReportLocation reportLocation(Festival festival, Long id) {
        ReportLocation location = ReportLocation.create(
                festival,
                SIGNATURE_NAME,
                LATITUDE,
                LONGITUDE);
        ReflectionTestUtils.setField(location, "id", id);
        return location;
    }

    private Report report(
            ReportLocation location,
            Long id,
            CongestionLevel congestionLevel,
            LocalDateTime createdAt
    ) {
        Report report = buildReport(
                user(),
                location,
                "제보 " + id,
                congestionLevel,
                IMAGE_URL);
        ReflectionTestUtils.setField(report, "id", id);
        ReflectionTestUtils.setField(report, "createdAt", createdAt);
        return report;
    }

    private Report buildReport(
            User user,
            ReportLocation location,
            String content,
            CongestionLevel congestionLevel,
            String imageUrl
    ) {
        return Report.builder()
                .user(user)
                .reportLocation(location)
                .content(content)
                .congestionLevel(congestionLevel)
                .imageUrl(imageUrl)
                .build();
    }

    private ReportLikeCount likeCount(Long reportId, Long count) {
        return new ReportLikeCount() {
            @Override
            public Long getReportId() {
                return reportId;
            }

            @Override
            public Long getLikeCount() {
                return count;
            }
        };
    }
}
