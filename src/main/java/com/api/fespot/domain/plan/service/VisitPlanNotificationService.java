package com.api.fespot.domain.plan.service;

import com.api.fespot.domain.notification.service.PushDeliveryService;
import com.api.fespot.domain.plan.entity.VisitPlan;
import com.api.fespot.domain.plan.repository.VisitPlanRepository;
import com.api.fespot.global.push.client.dto.ExpoPushMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class VisitPlanNotificationService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalTime SEND_WINDOW_START = LocalTime.of(8, 0);
    private static final LocalTime SEND_WINDOW_END = LocalTime.of(10, 0);
    private static final int BATCH_SIZE = 100;
    private static final String NOTIFICATION_TITLE = "방문 계획 알림";
    private static final String NOTIFICATION_BODY =
            "오늘은 계획한 여행이 시작되는 날이에요, 계획을 다시 한번 확인해볼까요?";

    private final VisitPlanRepository visitPlanRepository;
    private final PushDeliveryService pushDeliveryService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public VisitPlanNotificationService(
            VisitPlanRepository visitPlanRepository,
            PushDeliveryService pushDeliveryService,
            PlatformTransactionManager transactionManager
    ) {
        this(
                visitPlanRepository,
                pushDeliveryService,
                transactionManager,
                Clock.system(KOREA_ZONE)
        );
    }

    VisitPlanNotificationService(
            VisitPlanRepository visitPlanRepository,
            PushDeliveryService pushDeliveryService,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.visitPlanRepository = visitPlanRepository;
        this.pushDeliveryService = pushDeliveryService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public void processNotifications() {
        ZonedDateTime koreaTime = ZonedDateTime.now(clock).withZoneSameInstant(KOREA_ZONE);
        LocalTime time = koreaTime.toLocalTime();
        if (time.isBefore(SEND_WINDOW_START) || !time.isBefore(SEND_WINDOW_END)) {
            return;
        }

        LocalDate today = koreaTime.toLocalDate();
        Instant sendDeadline = today.atTime(SEND_WINDOW_END)
                .atZone(KOREA_ZONE)
                .toInstant();
        Long cursor = null;

        while (!hasReached(sendDeadline)) {
            List<Long> targets = loadTargets(today, cursor);
            if (targets.isEmpty()) {
                return;
            }

            for (Long planId : targets) {
                if (hasReached(sendDeadline)) {
                    return;
                }
                processTarget(planId, today, sendDeadline);
            }

            if (targets.size() < BATCH_SIZE) {
                return;
            }
            cursor = targets.get(targets.size() - 1);
        }
    }

    private List<Long> loadTargets(
            LocalDate today,
            Long cursor
    ) {
        return transactionTemplate.execute(status ->
                visitPlanRepository.findMorningNotificationTargets(
                        today,
                        cursor,
                        Limit.of(BATCH_SIZE))
        );
    }

    private void processTarget(
            Long planId,
            LocalDate today,
            Instant sendDeadline
    ) {
        try {
            ExpoPushMessage message = claimNotification(
                    planId,
                    today,
                    sendDeadline);

            if (message != null) {
                pushDeliveryService.sendBefore(
                        List.of(message),
                        sendDeadline);
            }
        } catch (RuntimeException exception) {
            log.error(
                    "방문 계획 아침 알림 처리 중 오류가 발생했습니다. planId={}",
                    planId,
                    exception
            );
        }
    }

    private ExpoPushMessage claimNotification(
            Long planId,
            LocalDate today,
            Instant sendDeadline
    ) {
        return transactionTemplate.execute(status -> {
            String pushToken = visitPlanRepository
                    .findMorningNotificationPushToken(planId, today)
                    .orElse(null);
            if (pushToken == null) {
                return null;
            }
            if (hasReached(sendDeadline)) {
                return null;
            }

            int updated = visitPlanRepository
                    .markMorningNotificationAttemptedIfEligible(
                            planId,
                            today,
                            pushToken);
            if (updated == 0) {
                return null;
            }

            return new ExpoPushMessage(
                    pushToken,
                    NOTIFICATION_TITLE,
                    NOTIFICATION_BODY,
                    Map.of(),
                    "default",
                    "default",
                    null
            );
        });
    }

    private boolean hasReached(Instant sendDeadline) {
        return !clock.instant().isBefore(sendDeadline);
    }
}
