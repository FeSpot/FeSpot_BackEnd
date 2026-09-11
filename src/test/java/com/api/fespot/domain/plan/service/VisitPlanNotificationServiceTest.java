package com.api.fespot.domain.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.fespot.domain.notification.service.PushDeliveryService;
import com.api.fespot.domain.plan.repository.VisitPlanRepository;
import com.api.fespot.global.push.client.dto.ExpoPushMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class VisitPlanNotificationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 7);
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final String PUSH_TOKEN = "ExponentPushToken[visit-plan]";
    private static final String SECOND_PUSH_TOKEN =
            "ExponentPushToken[second-visit-plan]";

    private final VisitPlanRepository visitPlanRepository =
            mock(VisitPlanRepository.class);
    private final PushDeliveryService pushDeliveryService =
            mock(PushDeliveryService.class);
    private final PlatformTransactionManager transactionManager =
            mock(PlatformTransactionManager.class);

    private MutableClock clock;
    private VisitPlanNotificationService notificationService;

    @BeforeEach
    void setUp() {
        given(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .willAnswer(invocation -> new SimpleTransactionStatus());
        clock = new MutableClock(at(8, 30).toInstant(), KOREA_ZONE);
        notificationService = new VisitPlanNotificationService(
                visitPlanRepository,
                pushDeliveryService,
                transactionManager,
                clock
        );
    }

    @Test
    void marksAttemptBeforeSendingFixedMessage() {
        givenTargets(1L);
        given(visitPlanRepository.findMorningNotificationPushToken(1L, TODAY))
                .willReturn(Optional.of(PUSH_TOKEN));
        given(visitPlanRepository.markMorningNotificationAttemptedIfEligible(
                1L,
                TODAY,
                PUSH_TOKEN)).willReturn(1);

        notificationService.processNotifications();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExpoPushMessage>> messagesCaptor =
                ArgumentCaptor.forClass(List.class);
        InOrder inOrder = inOrder(visitPlanRepository, pushDeliveryService);
        inOrder.verify(visitPlanRepository)
                .markMorningNotificationAttemptedIfEligible(
                        1L,
                        TODAY,
                        PUSH_TOKEN);
        inOrder.verify(pushDeliveryService).sendBefore(
                messagesCaptor.capture(),
                eq(at(10, 0).toInstant()));

        assertThat(messagesCaptor.getValue())
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.to()).isEqualTo(PUSH_TOKEN);
                    assertThat(message.title())
                            .isEqualTo("방문 계획 알림");
                    assertThat(message.body())
                            .isEqualTo(
                                    "오늘은 계획한 여행이 시작되는 날이에요, "
                                            + "계획을 다시 한번 확인해볼까요?");
                });
    }

    @Test
    void doesNotMarkAttemptWhenPushTokenDisappearsBeforeClaim() {
        givenTargets(1L);
        given(visitPlanRepository.findMorningNotificationPushToken(1L, TODAY))
                .willReturn(Optional.empty());

        notificationService.processNotifications();

        verify(visitPlanRepository, never())
                .markMorningNotificationAttemptedIfEligible(
                        any(),
                        any(),
                        any());
        verifyNoInteractions(pushDeliveryService);
    }

    @Test
    void doesNotRetryAfterExpoCallFailsOnceAttemptWasMarked() {
        givenTargets(1L);
        given(visitPlanRepository.findMorningNotificationPushToken(1L, TODAY))
                .willReturn(Optional.of(PUSH_TOKEN));
        given(visitPlanRepository.markMorningNotificationAttemptedIfEligible(
                        1L,
                        TODAY,
                        PUSH_TOKEN)).willReturn(1, 0);
        doThrow(new RuntimeException("Expo API failure"))
                .when(pushDeliveryService).sendBefore(any(), any());

        notificationService.processNotifications();
        clock.setInstant(at(8, 35).toInstant());
        notificationService.processNotifications();

        verify(pushDeliveryService).sendBefore(any(), any());
    }

    @Test
    void continuesWithOtherPlansWhenOneDeliveryFails() {
        givenTargets(2L, 1L);
        given(visitPlanRepository.findMorningNotificationPushToken(2L, TODAY))
                .willReturn(Optional.of(SECOND_PUSH_TOKEN));
        given(visitPlanRepository.findMorningNotificationPushToken(1L, TODAY))
                .willReturn(Optional.of(PUSH_TOKEN));
        given(visitPlanRepository.markMorningNotificationAttemptedIfEligible(
                2L,
                TODAY,
                SECOND_PUSH_TOKEN)).willReturn(1);
        given(visitPlanRepository.markMorningNotificationAttemptedIfEligible(
                1L,
                TODAY,
                PUSH_TOKEN)).willReturn(1);
        doThrow(new RuntimeException("Expo API failure"))
                .doNothing()
                .when(pushDeliveryService).sendBefore(any(), any());

        notificationService.processNotifications();

        verify(pushDeliveryService, times(2)).sendBefore(any(), any());
    }

    @Test
    void doesNotMarkAttemptWhenWindowEndsBeforeClaim() {
        givenTargets(1L);
        clock.setInstant(at(9, 59).toInstant());
        given(visitPlanRepository.findMorningNotificationPushToken(1L, TODAY))
                .willAnswer(invocation -> {
                    clock.setInstant(at(10, 0).toInstant());
                    return Optional.of(PUSH_TOKEN);
                });

        notificationService.processNotifications();

        verify(visitPlanRepository, never())
                .markMorningNotificationAttemptedIfEligible(
                        any(),
                        any(),
                        any());
        verifyNoInteractions(pushDeliveryService);
    }

    @Test
    void stopsBeforeProcessingNextPlanWhenWindowEnds() {
        givenTargets(2L, 1L);
        clock.setInstant(at(9, 59).toInstant());
        given(visitPlanRepository.findMorningNotificationPushToken(2L, TODAY))
                .willReturn(Optional.of(SECOND_PUSH_TOKEN));
        given(visitPlanRepository.markMorningNotificationAttemptedIfEligible(
                2L,
                TODAY,
                SECOND_PUSH_TOKEN)).willReturn(1);
        doAnswer(invocation -> {
            clock.setInstant(at(10, 0).toInstant());
            return null;
        }).when(pushDeliveryService).sendBefore(any(), any());

        notificationService.processNotifications();

        verify(pushDeliveryService).sendBefore(any(), any());
        verify(visitPlanRepository, never())
                .findMorningNotificationPushToken(1L, TODAY);
    }

    @Test
    void doesNotLoadNextBatchWhenWindowEndsAfterLastPlan() {
        givenTargets(LongStream.rangeClosed(1, 100)
                .map(id -> 101 - id)
                .boxed()
                .toArray(Long[]::new));
        clock.setInstant(at(9, 59).toInstant());
        given(visitPlanRepository.findMorningNotificationPushToken(1L, TODAY))
                .willReturn(Optional.of(PUSH_TOKEN));
        given(visitPlanRepository.markMorningNotificationAttemptedIfEligible(
                1L,
                TODAY,
                PUSH_TOKEN)).willReturn(1);
        doAnswer(invocation -> {
            clock.setInstant(at(10, 0).toInstant());
            return null;
        }).when(pushDeliveryService).sendBefore(any(), any());

        notificationService.processNotifications();

        verify(pushDeliveryService).sendBefore(any(), any());
        verify(visitPlanRepository).findMorningNotificationTargets(
                eq(TODAY),
                any(),
                eq(Limit.of(100)));
    }

    @ParameterizedTest
    @CsvSource({"7, 59", "10, 0", "10, 1"})
    void doesNothingOutsideTheMorningSendWindow(int hour, int minute) {
        clock.setInstant(at(hour, minute).toInstant());

        notificationService.processNotifications();

        verifyNoInteractions(visitPlanRepository, pushDeliveryService);
    }

    @Test
    void usesKoreanDateAndWindowStartWithUtcClock() {
        givenTargets();
        notificationService = new VisitPlanNotificationService(
                visitPlanRepository,
                pushDeliveryService,
                transactionManager,
                Clock.fixed(at(8, 0).toInstant(), ZoneOffset.UTC)
        );

        notificationService.processNotifications();

        verify(visitPlanRepository).findMorningNotificationTargets(TODAY, null, Limit.of(100));
    }

    private void givenTargets(Long... targets) {
        given(visitPlanRepository.findMorningNotificationTargets(
                TODAY,
                null,
                Limit.of(100))).willReturn(List.of(targets));
    }

    private ZonedDateTime at(int hour, int minute) {
        return TODAY.atTime(hour, minute).atZone(KOREA_ZONE);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
