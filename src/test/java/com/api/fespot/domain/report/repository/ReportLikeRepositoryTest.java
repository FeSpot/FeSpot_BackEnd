package com.api.fespot.domain.report.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.report.entity.CongestionLevel;
import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.entity.ReportLike;
import com.api.fespot.domain.report.entity.ReportLocation;
import com.api.fespot.domain.user.entity.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ReportLikeRepositoryTest {

    @Container
    @ServiceConnection
    private static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ReportLikeRepository reportLikeRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void countsRequestedReportsAndFindsCurrentUsersLikedReportIds() {
        Festival festival = entityManager.persist(Festival.create("100", 2026));
        User firstUser = entityManager.persist(User.create(
                "first-user@example.com",
                "첫번째 사용자"));
        User secondUser = entityManager.persist(User.create(
                "second-user@example.com",
                "두번째 사용자"));
        ReportLocation location = entityManager.persist(ReportLocation.create(
                festival,
                "메인 무대",
                35.1532,
                129.1187));
        Report firstReport = persistReport(firstUser, location, "첫 번째 제보");
        Report secondReport = persistReport(firstUser, location, "두 번째 제보");
        Report outsideRequest = persistReport(firstUser, location, "조회 대상 외부 제보");
        entityManager.persist(ReportLike.create(firstUser, firstReport));
        entityManager.persist(ReportLike.create(firstUser, secondReport));
        entityManager.persist(ReportLike.create(secondUser, firstReport));
        entityManager.persist(ReportLike.create(secondUser, outsideRequest));
        entityManager.flush();
        entityManager.clear();

        List<Long> requestedReportIds =
                List.of(firstReport.getId(), secondReport.getId());

        List<ReportLikeCount> counts =
                reportLikeRepository.countByReportIds(requestedReportIds);
        List<Long> likedReportIds = reportLikeRepository.findLikeReportIds(
                firstUser.getId(),
                requestedReportIds);

        assertThat(counts)
                .extracting(
                        ReportLikeCount::getReportId,
                        ReportLikeCount::getLikeCount)
                .containsExactlyInAnyOrder(
                        tuple(firstReport.getId(), 2L),
                        tuple(secondReport.getId(), 1L));
        assertThat(likedReportIds)
                .containsExactlyInAnyOrder(
                        firstReport.getId(),
                        secondReport.getId());
    }

    @Test
    void rejectsDuplicateLikeForSameUserAndReport() {
        Festival festival = entityManager.persist(Festival.create("200", 2026));
        User user = entityManager.persist(User.create(
                "duplicate-like@example.com",
                "사용자"));
        ReportLocation location = entityManager.persist(ReportLocation.create(
                festival,
                "메인 무대",
                35.1532,
                129.1187));
        Report report = persistReport(user, location, "제보");
        reportLikeRepository.saveAndFlush(ReportLike.create(user, report));

        assertThatThrownBy(() -> reportLikeRepository.saveAndFlush(
                ReportLike.create(user, report)))
                .isInstanceOf(DataIntegrityViolationException.class);
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
                .imageUrl(null)
                .build());
    }
}
