package com.api.fespot.domain.report.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalNotification;
import com.api.fespot.domain.festival.repository.FestivalNotificationRepository;
import com.api.fespot.domain.notification.entity.PushToken;
import com.api.fespot.domain.report.entity.CongestionLevel;
import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.entity.ReportLike;
import com.api.fespot.domain.report.entity.ReportLocation;
import com.api.fespot.domain.user.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Limit;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PopularReportNotificationRepositoryTest {

    @Container
    @ServiceConnection
    private static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private FestivalNotificationRepository festivalNotificationRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void selectsAndMarksOnlyActivePopularReportWithMatchingSubscribers() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 12, 0);
        Festival eligibleFestival = persistFestival("100");
        Festival noSubscriberFestival = persistFestival("200");
        Festival noTokenFestival = persistFestival("300");
        User reporter = persistUser("reporter@example.com");
        List<User> likers = IntStream.rangeClosed(1, 5)
                .mapToObj(number -> persistUser("liker" + number + "@example.com"))
                .toList();

        ReportLocation eligibleLocation = persistLocation(eligibleFestival);
        Report eligible = persistReport(reporter, eligibleLocation, "인기 제보");
        Report belowThreshold = persistReport(reporter, eligibleLocation, "좋아요 4개 제보");
        Report expired = persistReport(reporter, eligibleLocation, "만료된 제보");
        Report alreadyAttempted = persistReport(reporter, eligibleLocation, "이미 시도한 제보");
        Report withoutSubscriber = persistReport(
                reporter,
                persistLocation(noSubscriberFestival),
                "구독자 없는 제보");
        Report withoutToken = persistReport(
                reporter,
                persistLocation(noTokenFestival),
                "토큰 없는 제보");

        persistLikes(eligible, likers, 5);
        persistLikes(belowThreshold, likers, 4);
        persistLikes(expired, likers, 5);
        persistLikes(alreadyAttempted, likers, 5);
        persistLikes(withoutSubscriber, likers, 5);
        persistLikes(withoutToken, likers, 5);

        User subscriber = persistUser("subscriber@example.com");
        entityManager.persist(FestivalNotification.create(subscriber, eligibleFestival));
        String pushToken = "ExponentPushToken[popular-report]";
        entityManager.persist(PushToken.create(subscriber, pushToken));
        User subscriberWithoutToken = persistUser("no-token@example.com");
        entityManager.persist(FestivalNotification.create(
                subscriberWithoutToken,
                noTokenFestival));
        entityManager.flush();

        updateNotificationTimes(eligible, now.minusMinutes(5), null);
        updateNotificationTimes(belowThreshold, now.minusMinutes(5), null);
        updateNotificationTimes(expired, now.minusMinutes(31), null);
        updateNotificationTimes(alreadyAttempted, now.minusMinutes(5), now.minusMinutes(1));
        updateNotificationTimes(withoutSubscriber, now.minusMinutes(5), null);
        updateNotificationTimes(withoutToken, now.minusMinutes(5), null);
        entityManager.clear();

        List<Long> targets = reportRepository.findPopularNotificationTargets(
                now.minusMinutes(30),
                now,
                5,
                null,
                Limit.of(100));
        List<Long> recipientIds = festivalNotificationRepository
                .findSubscriberIdsWithPushToken(eligibleFestival.getId());
        List<String> currentTokens = festivalNotificationRepository
                .findCurrentPushTokens(eligibleFestival.getId(), recipientIds);
        boolean stillEligible = reportRepository.isPopularNotificationEligible(
                eligible.getId(),
                now.minusMinutes(30),
                now,
                5);
        int firstUpdate = reportRepository.markPopularNotificationAttemptedIfEligible(
                eligible.getId(),
                now.minusMinutes(30),
                now,
                5);
        int repeatedUpdate = reportRepository.markPopularNotificationAttemptedIfEligible(
                eligible.getId(),
                now.minusMinutes(30),
                now,
                5);

        assertThat(targets).containsExactly(eligible.getId());
        assertThat(recipientIds).containsExactly(subscriber.getId());
        assertThat(currentTokens).containsExactly(pushToken);
        assertThat(stillEligible).isTrue();
        assertThat(firstUpdate).isEqualTo(1);
        assertThat(repeatedUpdate).isZero();
        assertThat(reportRepository.findById(eligible.getId()).orElseThrow()
                .getPopularNotificationAttemptedAt()).isEqualTo(now);
        assertThat(reportRepository.findPopularNotificationTargets(
                now.minusMinutes(30),
                now,
                5,
                null,
                Limit.of(100))).isEmpty();
    }

    @Test
    void reportEditDoesNotOverwriteAttemptRecordedAfterEntityWasLoaded() {
        LocalDateTime attemptedAt = LocalDateTime.of(2026, 9, 10, 12, 0);
        Festival festival = persistFestival("dynamic-update");
        User reporter = persistUser("dynamic-update@example.com");
        Report report = persistReport(
                reporter,
                persistLocation(festival),
                "수정 전 제보");
        entityManager.flush();
        entityManager.clear();

        Report staleReport = reportRepository.findById(report.getId()).orElseThrow();
        entityManager.getEntityManager()
                .createNativeQuery("""
                        update reports
                        set popular_notification_attempted_at = :attemptedAt
                        where report_id = :reportId
                        """)
                .setParameter("attemptedAt", attemptedAt)
                .setParameter("reportId", report.getId())
                .executeUpdate();

        staleReport.update(null, "수정 후 제보", null);
        entityManager.flush();
        entityManager.clear();

        Report updatedReport = reportRepository.findById(report.getId()).orElseThrow();
        assertThat(updatedReport.getContent()).isEqualTo("수정 후 제보");
        assertThat(updatedReport.getPopularNotificationAttemptedAt())
                .isEqualTo(attemptedAt);
    }

    private Festival persistFestival(String contentId) {
        return entityManager.persist(Festival.create(contentId, 2026));
    }

    private User persistUser(String email) {
        return entityManager.persist(User.create(email, "사용자"));
    }

    private ReportLocation persistLocation(Festival festival) {
        return entityManager.persist(ReportLocation.create(
                festival,
                35.1532,
                129.1187));
    }

    private Report persistReport(
            User user,
            ReportLocation location,
            String content
    ) {
        return entityManager.persist(Report.builder()
                .user(user)
                .reportLocation(location)
                .content(content)
                .congestionLevel(CongestionLevel.NORMAL)
                .build());
    }

    private void persistLikes(Report report, List<User> users, int count) {
        users.stream()
                .limit(count)
                .map(user -> ReportLike.create(user, report))
                .forEach(entityManager::persist);
    }

    private void updateNotificationTimes(
            Report report,
            LocalDateTime createdAt,
            LocalDateTime attemptedAt
    ) {
        entityManager.getEntityManager()
                .createNativeQuery("""
                        update reports
                        set created_at = :createdAt,
                            popular_notification_attempted_at = :attemptedAt
                        where report_id = :reportId
                        """)
                .setParameter("createdAt", createdAt)
                .setParameter("attemptedAt", attemptedAt)
                .setParameter("reportId", report.getId())
                .executeUpdate();
    }
}
