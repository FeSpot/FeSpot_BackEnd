package com.api.fespot.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.repository.FestivalNotificationRepository;
import com.api.fespot.domain.notification.service.PushDeliveryService;
import com.api.fespot.domain.report.entity.CongestionLevel;
import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.entity.ReportLocation;
import com.api.fespot.domain.report.repository.ReportLikeRepository;
import com.api.fespot.domain.report.repository.ReportRepository;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.global.push.client.dto.ExpoPushMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PopularReportNotificationServiceTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-09-10T03:00:00Z");
    private static final LocalDateTime KOREA_NOW =
            LocalDateTime.of(2026, 9, 10, 12, 0);
    private static final Long REPORT_ID = 10L;
    private static final Long FESTIVAL_ID = 20L;
    private static final String CONTENT_ID = "festival-100";
    private static final String PUSH_TOKEN = "ExponentPushToken[popular-report]";

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final ReportLikeRepository reportLikeRepository =
            mock(ReportLikeRepository.class);
    private final FestivalNotificationRepository festivalNotificationRepository =
            mock(FestivalNotificationRepository.class);
    private final PushDeliveryService pushDeliveryService =
            mock(PushDeliveryService.class);
    private final PlatformTransactionManager transactionManager =
            mock(PlatformTransactionManager.class);

    private PopularReportNotificationService notificationService;

    @BeforeEach
    void setUp() {
        given(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .willAnswer(invocation -> new SimpleTransactionStatus());
        notificationService = new PopularReportNotificationService(
                reportRepository,
                reportLikeRepository,
                festivalNotificationRepository,
                pushDeliveryService,
                transactionManager,
                Clock.fixed(NOW, KOREA_ZONE)
        );
    }

    @Test
    void marksAttemptBeforeSendingFixedMessageToCurrentSubscribers() {
        Report report = report();
        givenTarget(REPORT_ID);
        givenEligibleClaim(report, List.of(1L, 2L), 1);
        given(reportRepository.isPopularNotificationEligible(
                REPORT_ID,
                KOREA_NOW.minusMinutes(30),
                KOREA_NOW,
                5)).willReturn(true);
        given(festivalNotificationRepository.findCurrentPushTokens(
                FESTIVAL_ID,
                List.of(1L, 2L))).willReturn(List.of(PUSH_TOKEN));

        notificationService.processNotifications();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExpoPushMessage>> messagesCaptor =
                ArgumentCaptor.forClass(List.class);
        InOrder inOrder = inOrder(reportRepository, pushDeliveryService);
        inOrder.verify(reportRepository).markPopularNotificationAttemptedIfEligible(
                REPORT_ID,
                KOREA_NOW.minusMinutes(30),
                KOREA_NOW,
                5);
        inOrder.verify(pushDeliveryService).send(messagesCaptor.capture());

        assertThat(messagesCaptor.getValue())
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.to()).isEqualTo(PUSH_TOKEN);
                    assertThat(message.title()).isEqualTo("인기 제보 알림");
                    assertThat(message.body()).isEqualTo(
                            "좋아요가 많이 있는 제보가 있어요. 한번 확인해볼까요?");
                    assertThat(message.data()).containsEntry("reportId", REPORT_ID);
                    assertThat(message.data()).containsEntry("contentId", CONTENT_ID);
                    assertThat(message.data()).containsEntry("year", 2026);
                });
    }

    @Test
    void doesNotMarkAttemptWhenThereAreNoRecipients() {
        Report report = report();
        givenTarget(REPORT_ID);
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));
        given(reportLikeRepository.countByReport_Id(REPORT_ID)).willReturn(5L);
        given(festivalNotificationRepository.findSubscriberIdsWithPushToken(FESTIVAL_ID))
                .willReturn(List.of());

        notificationService.processNotifications();

        verify(reportRepository, never()).markPopularNotificationAttemptedIfEligible(
                any(), any(), any(), anyLong());
        verifyNoInteractions(pushDeliveryService);
    }

    @Test
    void doesNotSendWhenAnotherExecutionAlreadyMarkedTheReport() {
        Report report = report();
        givenTarget(REPORT_ID);
        givenEligibleClaim(report, List.of(1L), 0);

        notificationService.processNotifications();

        verifyNoInteractions(pushDeliveryService);
    }

    @Test
    void excludesRecipientsWhoTurnedOffNotificationsBeforeSending() {
        Report report = report();
        givenTarget(REPORT_ID);
        givenEligibleClaim(report, List.of(1L), 1);
        given(reportRepository.isPopularNotificationEligible(
                REPORT_ID,
                KOREA_NOW.minusMinutes(30),
                KOREA_NOW,
                5)).willReturn(true);
        given(festivalNotificationRepository.findCurrentPushTokens(
                FESTIVAL_ID,
                List.of(1L))).willReturn(List.of());

        notificationService.processNotifications();

        verifyNoInteractions(pushDeliveryService);
    }

    @Test
    void stopsSendingWhenReportIsNoLongerPopularOrActive() {
        Report report = report();
        givenTarget(REPORT_ID);
        givenEligibleClaim(report, List.of(1L), 1);
        given(reportRepository.isPopularNotificationEligible(
                REPORT_ID,
                KOREA_NOW.minusMinutes(30),
                KOREA_NOW,
                5)).willReturn(false);

        notificationService.processNotifications();

        verify(festivalNotificationRepository, never())
                .findCurrentPushTokens(any(), any());
        verifyNoInteractions(pushDeliveryService);
    }

    @Test
    void continuesWithNextRecipientBatchWhenOneBatchFails() {
        Report report = report();
        List<Long> recipientIds = LongStream.rangeClosed(1, 101)
                .boxed()
                .toList();
        List<Long> firstBatch = recipientIds.subList(0, 100);
        List<Long> secondBatch = recipientIds.subList(100, 101);
        givenTarget(REPORT_ID);
        givenEligibleClaim(report, recipientIds, 1);
        given(reportRepository.isPopularNotificationEligible(
                eq(REPORT_ID),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq(5L))).willReturn(true);
        given(festivalNotificationRepository.findCurrentPushTokens(
                FESTIVAL_ID,
                firstBatch)).willReturn(List.of(PUSH_TOKEN));
        given(festivalNotificationRepository.findCurrentPushTokens(
                FESTIVAL_ID,
                secondBatch)).willReturn(List.of("ExponentPushToken[second-batch]"));
        doThrow(new RuntimeException("Expo API failure"))
                .doNothing()
                .when(pushDeliveryService).send(any());

        notificationService.processNotifications();

        verify(pushDeliveryService, times(2)).send(any());
    }

    @Test
    void continuesWithNextReportWhenOneReportFails() {
        Long failedReportId = 11L;
        Report report = report();
        given(reportRepository.findPopularNotificationTargets(
                KOREA_NOW.minusMinutes(30),
                KOREA_NOW,
                5,
                null,
                Limit.of(100))).willReturn(List.of(failedReportId, REPORT_ID));
        given(reportRepository.findById(failedReportId))
                .willThrow(new RuntimeException("DB failure"));
        givenEligibleClaim(report, List.of(1L), 1);
        given(reportRepository.isPopularNotificationEligible(
                REPORT_ID,
                KOREA_NOW.minusMinutes(30),
                KOREA_NOW,
                5)).willReturn(true);
        given(festivalNotificationRepository.findCurrentPushTokens(
                FESTIVAL_ID,
                List.of(1L))).willReturn(List.of(PUSH_TOKEN));

        notificationService.processNotifications();

        verify(pushDeliveryService).send(any());
    }

    private void givenTarget(Long reportId) {
        given(reportRepository.findPopularNotificationTargets(
                KOREA_NOW.minusMinutes(30),
                KOREA_NOW,
                5,
                null,
                Limit.of(100))).willReturn(List.of(reportId));
    }

    private void givenEligibleClaim(
            Report report,
            List<Long> recipientIds,
            int updated
    ) {
        given(reportRepository.findById(REPORT_ID)).willReturn(Optional.of(report));
        given(reportLikeRepository.countByReport_Id(REPORT_ID)).willReturn(5L);
        given(festivalNotificationRepository.findSubscriberIdsWithPushToken(FESTIVAL_ID))
                .willReturn(recipientIds);
        given(reportRepository.markPopularNotificationAttemptedIfEligible(
                REPORT_ID,
                KOREA_NOW.minusMinutes(30),
                KOREA_NOW,
                5)).willReturn(updated);
    }

    private Report report() {
        Festival festival = Festival.create(CONTENT_ID, 2026);
        ReflectionTestUtils.setField(festival, "id", FESTIVAL_ID);
        ReportLocation location = ReportLocation.create(
                festival,
                35.1532,
                129.1187);
        Report report = Report.builder()
                .user(User.create("reporter@example.com", "제보자"))
                .reportLocation(location)
                .content("혼잡해요")
                .congestionLevel(CongestionLevel.CROWDED)
                .build();
        ReflectionTestUtils.setField(report, "id", REPORT_ID);
        ReflectionTestUtils.setField(
                report,
                "createdAt",
                KOREA_NOW.minusMinutes(5));
        return report;
    }
}
