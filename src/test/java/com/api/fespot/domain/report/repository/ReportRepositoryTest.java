package com.api.fespot.domain.report.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.report.entity.CongestionLevel;
import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.entity.ReportLocation;
import com.api.fespot.domain.user.entity.User;
import java.time.LocalDateTime;
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
class ReportRepositoryTest {

    @Container
    @ServiceConnection
    private static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findsOnlyFestivalReportsBeforeCursorInDescendingOrder() {
        Festival festival = entityManager.persist(Festival.create("100", 2026));
        Festival otherFestival = entityManager.persist(Festival.create("200", 2026));
        User user = entityManager.persist(User.create(
                "report-feed@example.com",
                "사용자"));
        ReportLocation location = entityManager.persist(ReportLocation.create(
                festival,
                35.1532,
                129.1187));
        ReportLocation otherLocation = entityManager.persist(ReportLocation.create(
                otherFestival,
                35.1532,
                129.1187));

        Report oldest = persistReport(user, location, "첫 번째 제보");
        Report middle = persistReport(user, location, "두 번째 제보");
        Report newest = persistReport(user, location, "세 번째 제보");
        persistReport(user, otherLocation, "다른 축제 제보");
        entityManager.flush();
        entityManager.clear();

        List<Report> firstPage = reportRepository.findFeedByFestival(
                festival,
                null,
                Limit.of(2));
        List<Report> secondPage = reportRepository.findFeedByFestival(
                festival,
                middle.getId(),
                Limit.of(2));

        assertThat(firstPage)
                .extracting(Report::getId)
                .containsExactly(newest.getId(), middle.getId());
        assertThat(secondPage)
                .extracting(Report::getId)
                .containsExactly(oldest.getId());
    }

    @Test
    void findsOnlyUserReportsBeforeCursorInIdDescendingOrder() {
        Festival festival = entityManager.persist(Festival.create("250", 2026));
        User user = entityManager.persist(User.create(
                "my-reports@example.com",
                "사용자"));
        User otherUser = entityManager.persist(User.create(
                "other-reports@example.com",
                "다른 사용자"));
        ReportLocation location = entityManager.persist(ReportLocation.create(
                festival,
                35.1532,
                129.1187));

        Report oldest = persistReport(user, location, "첫 번째 내 제보");
        persistReport(otherUser, location, "첫 번째 타인 제보");
        Report middle = persistReport(user, location, "두 번째 내 제보");
        persistReport(otherUser, location, "두 번째 타인 제보");
        Report newest = persistReport(user, location, "세 번째 내 제보");
        entityManager.flush();
        updateCreatedAt(oldest, LocalDateTime.of(2030, 1, 1, 0, 0));
        entityManager.clear();

        List<Report> firstPage = reportRepository.findByUser(
                user,
                null,
                Limit.of(2));
        List<Report> secondPage = reportRepository.findByUser(
                user,
                middle.getId(),
                Limit.of(2));

        assertThat(firstPage)
                .extracting(Report::getId)
                .containsExactly(newest.getId(), middle.getId());
        assertThat(secondPage)
                .extracting(Report::getId)
                .containsExactly(oldest.getId());
    }

    @Test
    void findsOwnedReportAndDeletesOnlyThatReport() {
        Festival festival = entityManager.persist(Festival.create("275", 2026));
        User user = entityManager.persist(User.create(
                "delete-report@example.com",
                "사용자"));
        User otherUser = entityManager.persist(User.create(
                "keep-report@example.com",
                "다른 사용자"));
        ReportLocation location = entityManager.persist(ReportLocation.create(
                festival,
                35.1532,
                129.1187));
        Report report = persistReport(user, location, "삭제할 제보");
        Report otherReport = persistReport(otherUser, location, "유지할 제보");
        entityManager.flush();
        entityManager.clear();

        User persistedUser = entityManager.find(User.class, user.getId());
        User persistedOtherUser = entityManager.find(User.class, otherUser.getId());
        Report ownedReport = reportRepository.findByUserAndId(
                        persistedUser,
                        report.getId())
                .orElseThrow();

        assertThat(reportRepository.findByUserAndId(
                persistedOtherUser,
                report.getId())).isEmpty();

        reportRepository.delete(ownedReport);
        reportRepository.flush();
        entityManager.clear();

        assertThat(reportRepository.findById(report.getId())).isEmpty();
        assertThat(reportRepository.findById(otherReport.getId())).isPresent();
    }

    @Test
    void findsOnlyActiveFestivalReportsInDescendingCreatedAtOrder() {
        Festival festival = entityManager.persist(Festival.create("300", 2026));
        Festival otherFestival = entityManager.persist(Festival.create("400", 2026));
        User user = entityManager.persist(User.create(
                "active-report@example.com",
                "사용자"));
        ReportLocation location = entityManager.persist(ReportLocation.create(
                festival,
                35.1532,
                129.1187));
        ReportLocation otherLocation = entityManager.persist(ReportLocation.create(
                otherFestival,
                35.1532,
                129.1187));
        LocalDateTime activeSince = LocalDateTime.of(2026, 8, 23, 12, 0);

        Report expired = persistReport(user, location, "만료된 제보");
        Report olderActive = persistReport(user, location, "이전 활성 제보");
        Report newestActive = persistReport(user, location, "최신 활성 제보");
        Report otherFestivalActive = persistReport(
                user,
                otherLocation,
                "다른 축제 활성 제보");
        entityManager.flush();
        updateCreatedAt(expired, activeSince.minusMinutes(1));
        updateCreatedAt(olderActive, activeSince.plusMinutes(5));
        updateCreatedAt(newestActive, activeSince.plusMinutes(10));
        updateCreatedAt(otherFestivalActive, activeSince.plusMinutes(15));
        entityManager.clear();

        List<Report> activeReports = reportRepository.findActiveByFestival(
                festival,
                activeSince);

        assertThat(activeReports)
                .extracting(Report::getId)
                .containsExactly(newestActive.getId(), olderActive.getId());
    }

    private Report persistReport(
            User user,
            ReportLocation location,
            String content
    ) {
        return entityManager.persist(Report.create(
                user,
                location,
                content,
                CongestionLevel.NORMAL,
                null));
    }

    private void updateCreatedAt(Report report, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("""
                        update reports
                        set created_at = :createdAt
                        where report_id = :reportId
                        """)
                .setParameter("createdAt", createdAt)
                .setParameter("reportId", report.getId())
                .executeUpdate();
    }
}
