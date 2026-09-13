package com.api.fespot.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.entity.ReportLike;
import com.api.fespot.domain.report.exception.ReportErrorResponseCode;
import com.api.fespot.domain.report.repository.ReportLikeRepository;
import com.api.fespot.domain.report.repository.ReportRepository;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.exception.UserErrorResponseCode;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReportLikeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long REPORT_ID = 10L;

    private final ReportLikeRepository reportLikeRepository =
            mock(ReportLikeRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final ReportLikeService reportLikeService = new ReportLikeService(
            reportLikeRepository,
            userRepository,
            reportRepository);

    @Test
    void createsLikeWhenLikedIsTrueAndLikeDoesNotExist() {
        User user = user();
        Report report = mock(Report.class);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));
        given(reportLikeRepository.findByUserAndReport(user, report))
                .willReturn(Optional.empty());

        reportLikeService.updateReportLike(USER_ID, REPORT_ID, true);

        ArgumentCaptor<ReportLike> captor =
                ArgumentCaptor.forClass(ReportLike.class);
        verify(reportLikeRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getReport()).isSameAs(report);
    }

    @Test
    void keepsLikeStateWhenLikeAlreadyExists() {
        User user = user();
        Report report = mock(Report.class);
        ReportLike reportLike = ReportLike.create(user, report);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));
        given(reportLikeRepository.findByUserAndReport(user, report))
                .willReturn(Optional.of(reportLike));

        reportLikeService.updateReportLike(USER_ID, REPORT_ID, true);

        verify(reportLikeRepository, never()).save(any(ReportLike.class));
        verify(reportLikeRepository, never()).delete(any(ReportLike.class));
    }

    @Test
    void deletesLikeWhenLikedIsFalseAndLikeExists() {
        User user = user();
        Report report = mock(Report.class);
        ReportLike reportLike = ReportLike.create(user, report);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));
        given(reportLikeRepository.findByUserAndReport(user, report))
                .willReturn(Optional.of(reportLike));

        reportLikeService.updateReportLike(USER_ID, REPORT_ID, false);

        verify(reportLikeRepository).delete(reportLike);
    }

    @Test
    void keepsUnlikeStateWhenLikeDoesNotExist() {
        User user = user();
        Report report = mock(Report.class);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));
        given(reportLikeRepository.findByUserAndReport(user, report))
                .willReturn(Optional.empty());

        reportLikeService.updateReportLike(USER_ID, REPORT_ID, false);

        verify(reportLikeRepository, never()).save(any(ReportLike.class));
        verify(reportLikeRepository, never()).delete(any(ReportLike.class));
    }

    @Test
    void rejectsTokenUserThatNoLongerExists() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                reportLikeService.updateReportLike(USER_ID, REPORT_ID, true))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(UserErrorResponseCode.USER_NOT_FOUND_404));

        verifyNoInteractions(reportRepository, reportLikeRepository);
    }

    @Test
    void rejectsReportThatDoesNotExist() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                reportLikeService.updateReportLike(USER_ID, REPORT_ID, true))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(
                                        ReportErrorResponseCode.REPORT_NOT_FOUND_404));

        verifyNoInteractions(reportLikeRepository);
    }

    private User user() {
        return User.create("user@example.com", "사용자");
    }
}
