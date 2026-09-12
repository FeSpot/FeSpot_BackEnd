package com.api.fespot.domain.report.service;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.repository.FestivalNotificationRepository;
import com.api.fespot.domain.notification.service.PushDeliveryService;
import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.repository.ReportLikeRepository;
import com.api.fespot.domain.report.repository.ReportRepository;
import com.api.fespot.global.push.client.dto.ExpoPushMessage;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
public class PopularReportNotificationService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final int MINIMUM_LIKE_COUNT = 5;
    private static final int REPORT_BATCH_SIZE = 100;
    private static final int RECIPIENT_BATCH_SIZE = 100;
    private static final int ACTIVE_MINUTES = 30;
    private static final String NOTIFICATION_TITLE = "인기 제보 알림";
    private static final String NOTIFICATION_BODY =
            "좋아요가 많이 있는 제보가 있어요. 한번 확인해볼까요?";

    private final ReportRepository reportRepository;
    private final ReportLikeRepository reportLikeRepository;
    private final FestivalNotificationRepository festivalNotificationRepository;
    private final PushDeliveryService pushDeliveryService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public PopularReportNotificationService(
            ReportRepository reportRepository,
            ReportLikeRepository reportLikeRepository,
            FestivalNotificationRepository festivalNotificationRepository,
            PushDeliveryService pushDeliveryService,
            PlatformTransactionManager transactionManager
    ) {
        this(
                reportRepository,
                reportLikeRepository,
                festivalNotificationRepository,
                pushDeliveryService,
                transactionManager,
                Clock.system(KOREA_ZONE)
        );
    }

    PopularReportNotificationService(
            ReportRepository reportRepository,
            ReportLikeRepository reportLikeRepository,
            FestivalNotificationRepository festivalNotificationRepository,
            PushDeliveryService pushDeliveryService,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.reportRepository = reportRepository;
        this.reportLikeRepository = reportLikeRepository;
        this.festivalNotificationRepository = festivalNotificationRepository;
        this.pushDeliveryService = pushDeliveryService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public void processNotifications() {
        Long cursor = null;

        while (true) {
            LocalDateTime now = now();
            Long batchCursor = cursor;
            List<Long> reportIds = transactionTemplate.execute(status ->
                    reportRepository.findPopularNotificationTargets(
                            now.minusMinutes(ACTIVE_MINUTES),
                            now,
                            MINIMUM_LIKE_COUNT,
                            batchCursor,
                            Limit.of(REPORT_BATCH_SIZE))
            );
            if (reportIds.isEmpty()) {
                return;
            }

            for (Long reportId : reportIds) {
                processReport(reportId);
            }

            if (reportIds.size() < REPORT_BATCH_SIZE) {
                return;
            }
            cursor = reportIds.get(reportIds.size() - 1);
        }
    }

    private void processReport(Long reportId) {
        try {
            NotificationTarget target = claim(reportId);
            if (target != null) {
                sendToRecipients(target);
            }
        } catch (RuntimeException exception) {
            log.error(
                    "인기 제보 알림 처리 중 오류가 발생했습니다. reportId={}",
                    reportId,
                    exception
            );
        }
    }

    private NotificationTarget claim(Long reportId) {
        return transactionTemplate.execute(status -> {
            LocalDateTime attemptedAt = now();
            LocalDateTime activeSince = attemptedAt.minusMinutes(ACTIVE_MINUTES);
            Report report = reportRepository.findById(reportId).orElse(null);
            if (report == null
                    || report.getPopularNotificationAttemptedAt() != null
                    || report.getCreatedAt().isBefore(activeSince)
                    || report.getCreatedAt().isAfter(attemptedAt)
                    || reportLikeRepository.countByReport_Id(reportId)
                    < MINIMUM_LIKE_COUNT) {
                return null;
            }

            Festival festival = report.getReportLocation().getFestival();
            List<Long> recipientIds = festivalNotificationRepository
                    .findSubscriberIdsWithPushToken(festival.getId());
            if (recipientIds.isEmpty()) {
                return null;
            }

            NotificationTarget target = new NotificationTarget(
                    reportId,
                    festival.getId(),
                    festival.getContentId(),
                    festival.getYear(),
                    List.copyOf(recipientIds)
            );
            int updated = reportRepository
                    .markPopularNotificationAttemptedIfEligible(
                            reportId,
                            activeSince,
                            attemptedAt,
                            MINIMUM_LIKE_COUNT);

            return updated == 1 ? target : null;
        });
    }

    private void sendToRecipients(NotificationTarget target) {
        for (int start = 0; start < target.recipientIds().size();
             start += RECIPIENT_BATCH_SIZE) {
            int end = Math.min(
                    start + RECIPIENT_BATCH_SIZE,
                    target.recipientIds().size());
            List<Long> recipientIds = target.recipientIds().subList(start, end);

            try {
                List<ExpoPushMessage> messages = prepareMessages(target, recipientIds);
                if (messages == null) {
                    return;
                }
                if (!messages.isEmpty()) {
                    pushDeliveryService.send(messages);
                }
            } catch (RuntimeException exception) {
                log.error(
                        "인기 제보 알림 묶음 처리 중 오류가 발생했습니다. "
                                + "reportId={} recipientStartIndex={}",
                        target.reportId(),
                        start,
                        exception
                );
            }
        }
    }

    private List<ExpoPushMessage> prepareMessages(
            NotificationTarget target,
            List<Long> recipientIds
    ) {
        return transactionTemplate.execute(status -> {
            LocalDateTime currentTime = now();
            if (!reportRepository.isPopularNotificationEligible(
                    target.reportId(),
                    currentTime.minusMinutes(ACTIVE_MINUTES),
                    currentTime,
                    MINIMUM_LIKE_COUNT)) {
                return null;
            }

            List<String> pushTokens = festivalNotificationRepository
                    .findCurrentPushTokens(target.festivalId(), recipientIds);
            List<ExpoPushMessage> messages = new ArrayList<>(pushTokens.size());
            for (String pushToken : pushTokens) {
                messages.add(new ExpoPushMessage(
                        pushToken,
                        NOTIFICATION_TITLE,
                        NOTIFICATION_BODY,
                        Map.of(
                                "reportId", target.reportId(),
                                "contentId", target.contentId(),
                                "year", target.year()
                        ),
                        "default",
                        "default",
                        null
                ));
            }
            return messages;
        });
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(KOREA_ZONE));
    }

    private record NotificationTarget(
            Long reportId,
            Long festivalId,
            String contentId,
            int year,
            List<Long> recipientIds
    ) {
    }
}
