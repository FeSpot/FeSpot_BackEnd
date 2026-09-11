package com.api.fespot.domain.report.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.report.entity.ReportLocation;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ReportLocationRepositoryTest {

    @Container
    @ServiceConnection
    private static final MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ReportLocationRepository reportLocationRepository;

    @Autowired
    private TestEntityManager entityManager;

    // 임계 시각과 같은 대표 위치는 만료로 보지 않고, 그보다 이전에 제보된 위치만 조회한다.
    @Test
    void findsOnlyLocationsLastReportedBeforeThreshold() {
        Festival festival = entityManager.persist(Festival.create("100", 2026));
        LocalDateTime threshold = LocalDateTime.of(2026, 8, 23, 12, 0);

        ReportLocation expired = persistLocation(festival, "만료 무대");
        ReportLocation boundary = persistLocation(festival, "경계 무대");
        ReportLocation active = persistLocation(festival, "활성 무대");
        entityManager.flush();
        updateLastReportedAt(expired, threshold.minusMinutes(1));
        updateLastReportedAt(boundary, threshold);
        updateLastReportedAt(active, threshold.plusMinutes(1));
        entityManager.clear();

        List<ReportLocation> expiredLocations =
                reportLocationRepository.findAllByLastReportedAtBefore(threshold);

        assertThat(expiredLocations)
                .extracting(ReportLocation::getId)
                .containsExactly(expired.getId());
    }

    private ReportLocation persistLocation(Festival festival, String signatureName) {
        return entityManager.persist(ReportLocation.create(
                festival,
                signatureName,
                35.1532,
                129.1187));
    }

    private void updateLastReportedAt(
            ReportLocation reportLocation,
            LocalDateTime lastReportedAt
    ) {
        entityManager.getEntityManager()
                .createNativeQuery("""
                        update report_locations
                        set last_reported_at = :lastReportedAt
                        where report_location_id = :reportLocationId
                        """)
                .setParameter("lastReportedAt", lastReportedAt)
                .setParameter("reportLocationId", reportLocation.getId())
                .executeUpdate();
    }
}
