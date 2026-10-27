package com.api.fespot.global.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.api.fespot.domain.festival.client.ai.AiFestivalClient;
import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalAvailability;
import com.api.fespot.domain.festival.entity.FestivalKey;
import com.api.fespot.domain.festival.entity.FestivalLike;
import com.api.fespot.domain.festival.entity.FestivalNotification;
import com.api.fespot.domain.festival.entity.FestivalSummaryReadModel;
import com.api.fespot.domain.festival.exception.DuplicateFestivalException;
import com.api.fespot.domain.festival.exception.DuplicateFestivalLikeException;
import com.api.fespot.domain.festival.exception.DuplicateFestivalNotificationException;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.festival.repository.FestivalLikeRepository;
import com.api.fespot.domain.festival.repository.FestivalNotificationRepository;
import com.api.fespot.domain.festival.repository.FestivalRepository;
import com.api.fespot.domain.festival.service.FestivalLikeService;
import com.api.fespot.domain.festival.service.FestivalNotificationService;
import com.api.fespot.domain.festival.service.FestivalService;
import com.api.fespot.domain.festival.web.dto.FestivalHomeRes;
import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.entity.ReportLike;
import com.api.fespot.domain.report.exception.DuplicateReportLikeException;
import com.api.fespot.domain.report.exception.ReportErrorResponseCode;
import com.api.fespot.domain.report.repository.ReportLikeRepository;
import com.api.fespot.domain.report.repository.ReportRepository;
import com.api.fespot.domain.report.service.ReportLikeService;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

@SpringJUnitConfig(WrapUniqueViolationServiceProxyTest.Config.class)
class WrapUniqueViolationServiceProxyTest {

    private static final Long USER_ID = 1L;
    private static final Long REPORT_ID = 10L;
    private static final int YEAR = 2026;
    private static final String CONTENT_ID = "100";

    @Autowired private FestivalService festivalService;
    @Autowired private FestivalLikeService festivalLikeService;
    @Autowired private FestivalNotificationService festivalNotificationService;
    @Autowired private ReportLikeService reportLikeService;

    @MockitoBean private FestivalRepository festivalRepository;
    @MockitoBean private FestivalJpaRepository festivalJpaRepository;
    @MockitoBean private AiFestivalClient aiFestivalClient;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private FestivalLikeRepository festivalLikeRepository;
    @MockitoBean private FestivalNotificationRepository festivalNotificationRepository;
    @MockitoBean private ReportRepository reportRepository;
    @MockitoBean private ReportLikeRepository reportLikeRepository;
    @MockitoBean private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUpTransaction() {
        given(transactionManager.getTransaction(any()))
                .willAnswer(invocation -> new SimpleTransactionStatus());
    }

    @Test
    void mapsFestivalRegistrationDuplicate() {
        given(festivalRepository.findByYear(YEAR)).willReturn(List.of(homeFestival()));
        DataIntegrityViolationException original = databaseFailure(
                ConstraintKind.UNIQUE, "uk_festival_contents_id_year");
        given(festivalJpaRepository.saveAll(any())).willThrow(original);

        Throwable thrown = catchThrowable(() -> festivalService.getHomeFestivals(YEAR));

        assertThat(thrown).isExactlyInstanceOf(DuplicateFestivalException.class)
                .cause().isSameAs(original);
        DuplicateFestivalException duplicate = (DuplicateFestivalException) thrown;
        assertThat(duplicate.getBaseResponseCode())
                .isSameAs(FestivalErrorResponseCode.FESTIVAL_ALREADY_EXIST_409);
        assertThat(duplicate.getBaseResponseCode().getHttpStatus()).isEqualTo(409);
    }

    @Test
    void mapsFestivalLikeDuplicate() {
        prepareFestivalToggle();
        DataIntegrityViolationException original = databaseFailure(
                ConstraintKind.UNIQUE, "uk_festival_like_user_festival");
        given(festivalLikeRepository.save(any(FestivalLike.class))).willThrow(original);

        Throwable thrown = catchThrowable(() ->
                festivalLikeService.updateLike(USER_ID, CONTENT_ID, YEAR, true));

        assertThat(thrown).isExactlyInstanceOf(DuplicateFestivalLikeException.class)
                .cause().isSameAs(original);
        DuplicateFestivalLikeException duplicate = (DuplicateFestivalLikeException) thrown;
        assertThat(duplicate.getBaseResponseCode())
                .isSameAs(FestivalErrorResponseCode.FESTIVAL_LIKE_ALREADY_EXIST_409);
        assertThat(duplicate.getBaseResponseCode().getHttpStatus()).isEqualTo(409);
    }

    @Test
    void mapsFestivalNotificationDuplicate() {
        prepareFestivalToggle();
        DataIntegrityViolationException original = databaseFailure(
                ConstraintKind.UNIQUE, "uk_festival_notification_user_festival");
        given(festivalNotificationRepository.save(any(FestivalNotification.class)))
                .willThrow(original);

        Throwable thrown = catchThrowable(() ->
                festivalNotificationService.updateNotification(USER_ID, CONTENT_ID, YEAR, true));

        assertThat(thrown).isExactlyInstanceOf(DuplicateFestivalNotificationException.class)
                .cause().isSameAs(original);
        DuplicateFestivalNotificationException duplicate =
                (DuplicateFestivalNotificationException) thrown;
        assertThat(duplicate.getBaseResponseCode())
                .isSameAs(FestivalErrorResponseCode.FESTIVAL_NOTIFICATION_ALREADY_EXIST_409);
        assertThat(duplicate.getBaseResponseCode().getHttpStatus()).isEqualTo(409);
    }

    @Test
    void mapsReportLikeDuplicate() {
        prepareReportLike();
        DataIntegrityViolationException original = databaseFailure(
                ConstraintKind.UNIQUE, "report_like_unique");
        given(reportLikeRepository.save(any(ReportLike.class))).willThrow(original);

        Throwable thrown = catchThrowable(() ->
                reportLikeService.updateReportLike(USER_ID, REPORT_ID, true));

        assertThat(thrown).isExactlyInstanceOf(DuplicateReportLikeException.class)
                .cause().isSameAs(original);
        DuplicateReportLikeException duplicate = (DuplicateReportLikeException) thrown;
        assertThat(duplicate.getBaseResponseCode())
                .isSameAs(ReportErrorResponseCode.REPORT_LIKE_ALREADY_EXIST);
        assertThat(duplicate.getBaseResponseCode().getHttpStatus()).isEqualTo(409);
    }

    @Test
    void wrapsFestivalLikeCommitFailure() {
        prepareFestivalToggle();
        given(festivalLikeRepository.save(any(FestivalLike.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        DataIntegrityViolationException original = databaseFailure(
                ConstraintKind.UNIQUE, "uk_festival_like_user_festival");
        doThrow(original).when(transactionManager).commit(any());

        Throwable thrown = catchThrowable(() ->
                festivalLikeService.updateLike(USER_ID, CONTENT_ID, YEAR, true));

        assertThat(thrown).isExactlyInstanceOf(DuplicateFestivalLikeException.class)
                .cause().isSameAs(original);
        InOrder writes = inOrder(festivalLikeRepository, transactionManager);
        writes.verify(festivalLikeRepository).save(any(FestivalLike.class));
        writes.verify(transactionManager).commit(any());
    }

    @Test
    void wrapsFestivalNotificationCommitFailure() {
        prepareFestivalToggle();
        given(festivalNotificationRepository.save(any(FestivalNotification.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        DataIntegrityViolationException original = databaseFailure(
                ConstraintKind.UNIQUE, "uk_festival_notification_user_festival");
        doThrow(original).when(transactionManager).commit(any());

        Throwable thrown = catchThrowable(() ->
                festivalNotificationService.updateNotification(USER_ID, CONTENT_ID, YEAR, true));

        assertThat(thrown).isExactlyInstanceOf(DuplicateFestivalNotificationException.class)
                .cause().isSameAs(original);
        InOrder writes = inOrder(festivalNotificationRepository, transactionManager);
        writes.verify(festivalNotificationRepository).save(any(FestivalNotification.class));
        writes.verify(transactionManager).commit(any());
    }

    @Test
    void wrapsReportLikeCommitFailure() {
        prepareReportLike();
        given(reportLikeRepository.save(any(ReportLike.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        DataIntegrityViolationException original = databaseFailure(
                ConstraintKind.UNIQUE, "report_like_unique");
        doThrow(original).when(transactionManager).commit(any());

        Throwable thrown = catchThrowable(() ->
                reportLikeService.updateReportLike(USER_ID, REPORT_ID, true));

        assertThat(thrown).isExactlyInstanceOf(DuplicateReportLikeException.class)
                .cause().isSameAs(original);
        InOrder writes = inOrder(reportLikeRepository, transactionManager);
        writes.verify(reportLikeRepository).save(any(ReportLike.class));
        writes.verify(transactionManager).commit(any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("otherDatabaseFailures")
    void preservesOtherDatabaseFailures(String scenario, DataIntegrityViolationException original) {
        given(festivalRepository.findByYear(YEAR)).willReturn(List.of(homeFestival()));
        given(festivalJpaRepository.saveAll(any())).willThrow(original);

        Throwable thrown = catchThrowable(() -> festivalService.getHomeFestivals(YEAR));

        assertThat(thrown).isSameAs(original);
    }

    @Test
    void preservesHomeResponseThroughServiceProxies() {
        FestivalSummaryReadModel festival = homeFestival();
        given(festivalRepository.findByYear(YEAR)).willReturn(List.of(festival));
        assertThat(List.of(festivalService, festivalLikeService,
                festivalNotificationService, reportLikeService))
                .allMatch(AopUtils::isAopProxy);

        List<FestivalHomeRes> responses = festivalService.getHomeFestivals(YEAR);

        assertThat(responses).containsExactly(
                FestivalHomeRes.available(FestivalKey.BUSAN_SEA_FESTIVAL, festival),
                FestivalHomeRes.unavailable(FestivalKey.BUSAN_INTERNATIONAL_FILM_FESTIVAL,
                        FestivalAvailability.NOT_FOUND),
                FestivalHomeRes.unavailable(FestivalKey.BUSAN_INTERNATIONAL_ROCK_FESTIVAL,
                        FestivalAvailability.NOT_FOUND));
        verify(festivalJpaRepository).saveAll(any());
    }

    @Test
    void commitsSuccessfulFestivalLike() {
        prepareFestivalToggle();
        given(festivalLikeRepository.save(any(FestivalLike.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        festivalLikeService.updateLike(USER_ID, CONTENT_ID, YEAR, true);

        InOrder writes = inOrder(festivalLikeRepository, transactionManager);
        writes.verify(festivalLikeRepository).save(any(FestivalLike.class));
        writes.verify(transactionManager).commit(any());
    }

    private static Stream<Arguments> otherDatabaseFailures() {
        return Stream.of(
                Arguments.of("another unique constraint",
                        databaseFailure(ConstraintKind.UNIQUE, "another_unique_constraint")),
                Arguments.of("matching name with OTHER kind",
                        databaseFailure(ConstraintKind.OTHER, "uk_festival_contents_id_year")),
                Arguments.of("missing Hibernate cause",
                        new DataIntegrityViolationException("write failed", new SQLException())),
                Arguments.of("missing constraint name",
                        databaseFailure(ConstraintKind.UNIQUE, null)));
    }

    private void prepareFestivalToggle() {
        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(User.create("user@example.com", "password-hash")));
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(Festival.create(CONTENT_ID, YEAR)));
    }

    private void prepareReportLike() {
        given(userRepository.findById(USER_ID))
                .willReturn(Optional.of(User.create("user@example.com", "password-hash")));
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(mock(Report.class)));
    }

    private FestivalSummaryReadModel homeFestival() {
        return new FestivalSummaryReadModel(
                CONTENT_ID,
                "부산바다축제",
                "다대포해수욕장",
                LocalDate.of(YEAR, 8, 1),
                LocalDate.of(YEAR, 8, 3),
                null,
                null,
                "한국관광공사 OpenAPI",
                Instant.parse("2026-07-22T00:00:00Z"));
    }

    private static DataIntegrityViolationException databaseFailure(
            ConstraintKind kind, String constraintName
    ) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "constraint violation", new SQLException("database write failed"),
                kind, constraintName);
        return new DataIntegrityViolationException("write failed", cause);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    @EnableTransactionManagement
    @Import({
            FestivalService.class,
            FestivalLikeService.class,
            FestivalNotificationService.class,
            ReportLikeService.class,
            WrapDataIntegrityViolationException.class
    })
    static class Config {
    }
}
