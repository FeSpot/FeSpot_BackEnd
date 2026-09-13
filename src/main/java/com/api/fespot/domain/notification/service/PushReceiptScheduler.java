package com.api.fespot.domain.notification.service;

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
public class PushReceiptScheduler {

    private static final long INITIAL_DELAY_MINUTES = 1L;
    private static final long FIXED_DELAY_MINUTES = 5L;

    private final PushReceiptService pushReceiptService;

    @Scheduled(
            initialDelay = INITIAL_DELAY_MINUTES,
            fixedDelay = FIXED_DELAY_MINUTES,
            timeUnit = TimeUnit.MINUTES
    )
    public void processPendingReceipts() {
        try {
            pushReceiptService.processPendingReceipts();
        } catch (RuntimeException exception) {
            log.error(
                    "Expo Push receipt 스케줄 처리 중 오류가 발생했습니다.",
                    exception
            );
        }
    }
}
