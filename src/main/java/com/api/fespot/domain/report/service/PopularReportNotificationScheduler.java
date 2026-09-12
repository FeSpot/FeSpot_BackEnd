package com.api.fespot.domain.report.service;

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
public class PopularReportNotificationScheduler {

    private static final long FIXED_DELAY_MINUTES = 1L;

    private final PopularReportNotificationService notificationService;

    @Scheduled(
            fixedDelay = FIXED_DELAY_MINUTES,
            timeUnit = TimeUnit.MINUTES
    )
    public void processPopularReportNotifications() {
        try {
            notificationService.processNotifications();
        } catch (RuntimeException exception) {
            log.error("인기 제보 알림 스케줄 처리 중 오류가 발생했습니다.", exception);
        }
    }
}
