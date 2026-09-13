package com.api.fespot.domain.plan.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalNotification;
import com.api.fespot.domain.notification.entity.PushToken;
import com.api.fespot.domain.plan.entity.VisitPlan;
import com.api.fespot.domain.user.entity.User;
import java.time.LocalDate;
import java.util.List;
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
class VisitPlanNotificationRepositoryTest {

    @Container
    @ServiceConnection
    private static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private VisitPlanRepository visitPlanRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findsOnlyPlansWithMatchingUserFestivalNotificationAndPushToken() {
        LocalDate today = LocalDate.of(2026, 8, 7);
        Festival festival = persistFestival("100", today.getYear());
        Festival otherFestival = persistFestival("200", today.getYear());

        User eligibleUser = persistUser("eligible@example.com");
        VisitPlan eligiblePlan = persistPlan(eligibleUser, festival, today);
        persistNotification(eligibleUser, festival);
        persistToken(eligibleUser, "ExponentPushToken[eligible]");

        User otherUser = persistUser("other-user@example.com");
        persistPlan(otherUser, festival, today);
        persistToken(otherUser, "ExponentPushToken[other-user]");

        persistPlan(eligibleUser, otherFestival, today);

        User notificationOffUser = persistUser("notification-off@example.com");
        persistPlan(notificationOffUser, festival, today);
        persistToken(notificationOffUser, "ExponentPushToken[notification-off]");

        User noTokenUser = persistUser("no-token@example.com");
        persistPlan(noTokenUser, festival, today);
        persistNotification(noTokenUser, festival);

        persistPlan(eligibleUser, festival, today.plusDays(1));
        entityManager.flush();
        entityManager.clear();

        List<Long> targets =
                visitPlanRepository.findMorningNotificationTargets(
                        today,
                        null,
                        Limit.of(100));

        assertThat(targets)
                .containsExactly(eligiblePlan.getId());
    }

    @Test
    void marksAttemptOnlyOnceForTheSamePlanAndVisitDate() {
        LocalDate today = LocalDate.of(2026, 8, 7);
        Festival festival = persistFestival("100", today.getYear());
        User user = persistUser("repeat@example.com");
        VisitPlan plan = persistPlan(user, festival, today);
        persistNotification(user, festival);
        String token = "ExponentPushToken[repeat]";
        persistToken(user, token);
        entityManager.flush();
        entityManager.clear();

        int firstUpdate = visitPlanRepository
                .markMorningNotificationAttemptedIfEligible(
                        plan.getId(),
                        today,
                        token);
        int repeatedUpdate = visitPlanRepository
                .markMorningNotificationAttemptedIfEligible(
                        plan.getId(),
                        today,
                        token);

        assertThat(firstUpdate).isEqualTo(1);
        assertThat(repeatedUpdate).isZero();
        assertThat(visitPlanRepository.findById(plan.getId()).orElseThrow()
                .getMorningNotificationAttemptedDate()).isEqualTo(today);
        assertThat(visitPlanRepository.findMorningNotificationTargets(
                today,
                null,
                Limit.of(100))).isEmpty();
    }

    private Festival persistFestival(String contentId, int year) {
        return entityManager.persist(Festival.create(contentId, year));
    }

    private User persistUser(String email) {
        return entityManager.persist(User.create(email, "사용자"));
    }

    private VisitPlan persistPlan(
            User user,
            Festival festival,
            LocalDate targetDate
    ) {
        return entityManager.persist(VisitPlan.builder()
                .user(user)
                .festival(festival)
                .planTargetDate(targetDate)
                .title("알림 테스트 계획")
                .build());
    }

    private void persistNotification(User user, Festival festival) {
        entityManager.persist(FestivalNotification.create(user, festival));
    }

    private void persistToken(User user, String token) {
        entityManager.persist(PushToken.create(user, token));
    }
}
