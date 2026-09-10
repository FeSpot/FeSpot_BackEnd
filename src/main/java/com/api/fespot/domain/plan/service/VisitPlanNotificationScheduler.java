package com.api.fespot.domain.plan.service;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class VisitPlanNotificationScheduler {

    private static final long FIXED_DELAY_MINUTES = 5L;

    private final VisitPlanNotificationService visitPlanNotificationService;

    @Scheduled(
            fixedDelay = FIXED_DELAY_MINUTES,
            timeUnit = TimeUnit.MINUTES
    )
    public void processVisitPlanNotifications() {
        try {
            visitPlanNotificationService.processNotifications();
        } catch (RuntimeException exception) {
            log.error(
                    "방문 계획 아침 알림 스케줄 처리 중 오류가 발생했습니다.",
                    exception
            );
        }
    }
}
