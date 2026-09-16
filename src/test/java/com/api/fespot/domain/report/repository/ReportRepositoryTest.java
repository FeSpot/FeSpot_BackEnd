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
    void findsFestivalFeedIncludingExpiredReportsWhenLocationFilterIsAbsent() {
        Festival festival = entityManager.persist(Festival.create("100", 2026));
        Festival otherFestival = entityManager.persist(Festival.create("200", 2026));
        User user = entityManager.persist(User.create(
                "report-feed@example.com",
                "사용자"));
        ReportLocation location = entityManager.persist(ReportLocation.create(
                festival,
                "메인 무대",
                35.1532,
                129.1187));
        ReportLocation otherLocation = entityManager.persist(ReportLocation.create(
                otherFestival,
                "메인 무대",
                35.1532,
                129.1187));

        Report oldest = persistReport(user, location, "첫 번째 제보");
        Report middle = persistReport(user, location, "두 번째 제보");
        Report newest = persistReport(user, location, "세 번째 제보");
        persistReport(user, otherLocation, "다른 축제 제보");
        LocalDateTime activeSince = LocalDateTime.of(2026, 8, 23, 12, 0);
        entityManager.flush();
        updateCreatedAt(oldest, activeSince.minusSeconds(1));
        updateCreatedAt(middle, activeSince);
        updateCreatedAt(newest, activeSince.plusMinutes(1));
        entityManager.clear();

        List<Report> firstPage = reportRepository.findFeedByFestival(
                festival,
                null,
                activeSince,
                null,
                Limit.of(2));
        List<Report> secondPage = reportRepository.findFeedByFestival(
                festival,
                null,
                activeSince,
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
    void filtersLocationAndActiveWindowBeforeApplyingCursorAndLimit() {
        Festival festival = entityManager.persist(Festival.create("110", 2026));
        Festival otherFestival = entityManager.persist(Festival.create("110", 2027));
        User user = entityManager.persist(User.create("location-feed@example.com", "사용자"));
        ReportLocation location = entityManager.persist(ReportLocation.create(
                festival, "메인 무대", 35.1532, 129.1187));
        ReportLocation sameNameLocation = entityManager.persist(ReportLocation.create(
                festival, "메인 무대", 35.1600, 129.1200));
        ReportLocation otherFestivalLocation = entityManager.persist(ReportLocation.create(
                otherFestival, "메인 무대", 35.1532, 129.1187));
        LocalDateTime activeSince = LocalDateTime.of(2026, 8, 23, 12, 0);

        Report boundary = persistReport(user, location, "정확히 30분 전 제보");
        Report middle = persistReport(user, location, "이전 활성 제보");
        Report newest = persistReport(user, location, "최신 활성 제보");
        newest.update(null, null, CongestionLevel.CROWDED);
        Report expired = persistReport(user, location, "30분을 지난 제보");
        Report otherLocationReport = persistReport(user, sameNameLocation, "동명 구역 제보");
        Report otherFestivalReport = persistReport(user, otherFestivalLocation, "다른 연도 축제 제보");
        entityManager.flush();
        updateCreatedAt(boundary, activeSince);
        updateCreatedAt(middle, activeSince.plusMinutes(1));
        updateCreatedAt(newest, activeSince.plusMinutes(2));
        updateCreatedAt(expired, activeSince.minusSeconds(1));
        updateCreatedAt(otherLocationReport, activeSince.plusMinutes(3));
        updateCreatedAt(otherFestivalReport, activeSince.plusMinutes(4));
        entityManager.clear();

        List<Report> firstPage = reportRepository.findFeedByFestival(
                festival, location.getId(), activeSince, null, Limit.of(2));
        List<Report> secondPage = reportRepository.findFeedByFestival(
                festival, location.getId(), activeSince, middle.getId(), Limit.of(2));

        assertThat(firstPage).extracting(Report::getId)
                .containsExactly(newest.getId(), middle.getId());
        assertThat(firstPage).extracting(Report::getCongestionLevel)
                .containsExactly(CongestionLevel.CROWDED, CongestionLevel.NORMAL);
        assertThat(secondPage).extracting(Report::getId)
                .containsExactly(boundary.getId());
        assertThat(reportRepository.findFeedByFestival(
                festival, otherFestivalLocation.getId(), activeSince, null, Limit.of(2)))
                .isEmpty();
        assertThat(reportRepository.findFeedByFestival(
                festival, location.getId(), activeSince, boundary.getId(), Limit.of(2)))
                .isEmpty();
    }

    @Test
    void returnsEmptyLocationFeedWhenOnlyExpiredReportsRemain() {
        Festival festival = entityManager.persist(Festival.create("120", 2026));
        User user = entityManager.persist(User.create("expired-location@example.com", "사용자"));
        ReportLocation location = entityManager.persist(ReportLocation.create(
                festival, null, 35.1532, 129.1187));
        Report expired = persistReport(user, location, "정리되지 않은 만료 제보");
        LocalDateTime activeSince = LocalDateTime.of(2026, 8, 23, 12, 0);
        entityManager.flush();
        updateCreatedAt(expired, activeSince.minusSeconds(1));
        entityManager.clear();

        assertThat(reportRepository.findFeedByFestival(
                festival, location.getId(), activeSince, null, Limit.of(11)))
                .isEmpty();
        assertThat(reportRepository.findById(expired.getId())).isPresent();
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
                "메인 무대",
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

        List<Report> firstPage = reportRepository.findCursorPageByUser(
                user,
                null,
                Limit.of(2));
        List<Report> secondPage = reportRepository.findCursorPageByUser(
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
                "메인 무대",
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
                "메인 무대",
                35.1532,
                129.1187));
        ReportLocation otherLocation = entityManager.persist(ReportLocation.create(
                otherFestival,
                "메인 무대",
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

    // 해당 대표 위치의 제보 중 이미지가 있는 제보의 URL만 조회한다.
    @Test
    void findsOnlyNonNullImageUrlsOfReportLocation() {
        Festival festival = entityManager.persist(Festival.create("500", 2026));
        User user = entityManager.persist(User.create(
                "cleanup-report@example.com",
                "사용자"));
        ReportLocation location = entityManager.persist(ReportLocation.create(
                festival,
                "메인 무대",
                35.1532,
                129.1187));
        ReportLocation otherLocation = entityManager.persist(ReportLocation.create(
                festival,
                "다른 무대",
                35.1600,
                129.1200));

        persistReport(user, location, "이미지 있는 제보", "https://fespot-bucket.s3.ap-northeast-2.amazonaws.com/report/a.jpg");
        persistReport(user, location, "이미지 없는 제보", null);
        persistReport(user, otherLocation, "다른 위치 제보", "https://fespot-bucket.s3.ap-northeast-2.amazonaws.com/report/b.jpg");
        entityManager.flush();
        entityManager.clear();

        List<String> imageUrls =
                reportRepository.findImageUrlsByReportLocationId(location.getId());

        assertThat(imageUrls)
                .containsExactly("https://fespot-bucket.s3.ap-northeast-2.amazonaws.com/report/a.jpg");
    }

    // 대표 위치에 딸린 제보들만 일괄 삭제한다.
    @Test
    void deletesAllReportsOfGivenReportLocations() {
        Festival festival = entityManager.persist(Festival.create("600", 2026));
        User user = entityManager.persist(User.create(
                "bulk-delete@example.com",
                "사용자"));
        ReportLocation expiredLocation = entityManager.persist(ReportLocation.create(
                festival,
                "만료 무대",
                35.1532,
                129.1187));
        ReportLocation activeLocation = entityManager.persist(ReportLocation.create(
                festival,
                "활성 무대",
                35.1600,
                129.1200));

        persistReport(user, expiredLocation, "만료 제보1");
        persistReport(user, expiredLocation, "만료 제보2");
        Report remaining = persistReport(user, activeLocation, "활성 제보");
        entityManager.flush();
        entityManager.clear();

        reportRepository.deleteAllByReportLocationIdIn(List.of(expiredLocation.getId()));
        entityManager.clear();

        assertThat(reportRepository.findAll())
                .extracting(Report::getId)
                .containsExactly(remaining.getId());
    }

    private Report persistReport(
            User user,
            ReportLocation location,
            String content
    ) {
        return persistReport(user, location, content, null);
    }

    private Report persistReport(
            User user,
            ReportLocation location,
            String content,
            String imageUrl
    ) {
        return entityManager.persist(Report.builder()
                .user(user)
                .reportLocation(location)
                .content(content)
                .originalLocation("메인 스테이지")
                .congestionLevel(CongestionLevel.NORMAL)
                .imageUrl(imageUrl)
                .build());
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
