package com.api.fespot.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalKey;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.festival.repository.FestivalRepository;
import com.api.fespot.domain.report.client.AiReportClient;
import com.api.fespot.domain.report.client.dto.AiNormalizedLocationRes;
import com.api.fespot.domain.report.entity.CongestionLevel;
import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.entity.ReportLocation;
import com.api.fespot.domain.report.exception.ReportErrorResponseCode;
import com.api.fespot.domain.report.repository.ReportLocationRepository;
import com.api.fespot.domain.report.repository.ReportRepository;
import com.api.fespot.domain.report.web.dto.ReportCreateReq;
import com.api.fespot.domain.report.web.dto.ReportFeedRes;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ReportService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class ReportCreationTransactionTest {

    @Container
    @ServiceConnection
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired private ReportService reportService;
    @Autowired private UserRepository userRepository;
    @Autowired private FestivalJpaRepository festivalJpaRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private ReportLocationRepository reportLocationRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @MockitoBean private FestivalRepository festivalRepository;
    @MockitoBean private AiReportClient aiReportClient;

    private User user;
    private Festival festival;

    @BeforeEach
    void setUp() {
        String key = UUID.randomUUID().toString();
        user = userRepository.save(User.create(key + "@example.com", "사용자"));
        festival = festivalJpaRepository.save(Festival.create(key, 2026, FestivalKey.BUSAN_SEA_FESTIVAL));
        given(aiReportClient.getNormalizedLocation("공연장", "busan_sea_festival"))
                .willAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return new AiNormalizedLocationRes("메인무대", true);
                });
    }

    @Test
    void callsAiAfterReportCommitUsingStoredFestivalKeyWithoutKtoLookup() {
        given(aiReportClient.getNormalizedLocation("공연장", "busan_sea_festival"))
                .willAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    assertThat(reportRepository.findActiveByFestival(festival, LocalDateTime.now().minusMinutes(30)))
                            .singleElement().satisfies(saved -> {
                                assertThat(saved.getOriginalLocation()).isEqualTo("공연장");
                                assertThat(saved.getReportLocation().getSignatureName()).isNull();
                            });
                    return new AiNormalizedLocationRes("메인무대", true);
                });

        Report report = createReport("공연장 앞에 사람이 많아요.");

        assertThat(reportRepository.existsById(report.getId())).isTrue();
        assertThat(reportRepository.findById(report.getId()).orElseThrow().getOriginalLocation())
                .isEqualTo("공연장");
        assertThat(reportLocationRepository.existsById(report.getReportLocation().getId())).isTrue();
        assertThat(reportLocationRepository.findById(report.getReportLocation().getId()).orElseThrow()
                .getSignatureName()).isEqualTo("메인무대");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        verifyNoInteractions(festivalRepository);
    }

    @Test
    void usesStoredRockFestivalKeyWithoutKtoLookup() {
        festival = festivalJpaRepository.save(Festival.create(
                UUID.randomUUID().toString(), 2026, FestivalKey.BUSAN_INTERNATIONAL_ROCK_FESTIVAL));
        given(aiReportClient.getNormalizedLocation("공연장", "busan_rock_festival"))
                .willReturn(new AiNormalizedLocationRes("메인무대", true));

        Report report = createReport("록페스티벌 공연장 앞의 제보입니다.");

        assertThat(reportLocationRepository.findById(report.getReportLocation().getId()).orElseThrow()
                .getSignatureName()).isEqualTo("메인무대");
        verifyNoInteractions(festivalRepository);
    }

    @Test
    void reusesExistingLocationWithoutCallingExternalApis() {
        Report existing = createReport("먼저 등록한 제보입니다.");
        clearInvocations(festivalRepository, aiReportClient);

        Report report = createReport("같은 위치에서 등록한 제보입니다.", "푸드트럭 앞", 35.1532);

        assertThat(report.getReportLocation().getId()).isEqualTo(existing.getReportLocation().getId());
        assertThat(report.getReportLocation().getSignatureName()).isEqualTo("메인무대");
        assertThat(reportRepository.findById(report.getId()).orElseThrow().getOriginalLocation())
                .isEqualTo("푸드트럭 앞");
        verifyNoInteractions(festivalRepository, aiReportClient);
    }

    @ParameterizedTest
    @MethodSource("unusableNormalizedLocations")
    void savesReportWhenNormalizedLocationIsUnmatchedOrEmpty(AiNormalizedLocationRes response) {
        given(aiReportClient.getNormalizedLocation("공연장", "busan_sea_festival"))
                .willReturn(response);
        long locationCount = reportLocationRepository.count();
        long reportCount = reportRepository.count();

        Report report = createReport("장소명이 정규화되지 않은 제보입니다.");

        assertThat(reportLocationRepository.count()).isEqualTo(locationCount + 1);
        assertThat(reportRepository.count()).isEqualTo(reportCount + 1);
        assertThat(reportRepository.findById(report.getId()).orElseThrow().getOriginalLocation()).isEqualTo("공연장");
        assertThat(reportLocationRepository.findById(report.getReportLocation().getId()).orElseThrow()
                .getSignatureName()).isNull();
    }

    private static Stream<AiNormalizedLocationRes> unusableNormalizedLocations() {
        return Stream.of(
                new AiNormalizedLocationRes(null, false),
                new AiNormalizedLocationRes("메인무대", false),
                new AiNormalizedLocationRes(null, true),
                new AiNormalizedLocationRes("", true),
                new AiNormalizedLocationRes(" \t", true));
    }

    @Test
    void savesReportWithoutExternalCallsWhenFestivalKeyIsMissing() {
        festival = festivalJpaRepository.save(Festival.create(UUID.randomUUID().toString(), 2026));
        long locationCount = reportLocationRepository.count();
        long reportCount = reportRepository.count();

        Report report = createReport("미지원 축제에 등록하는 제보입니다.");

        assertThat(reportLocationRepository.count()).isEqualTo(locationCount + 1);
        assertThat(reportRepository.count()).isEqualTo(reportCount + 1);
        assertThat(reportLocationRepository.findById(report.getReportLocation().getId()).orElseThrow()
                .getSignatureName()).isNull();
        verifyNoInteractions(festivalRepository, aiReportClient);
    }

    @Test
    void keepsReportWhenAiApiFails() {
        given(aiReportClient.getNormalizedLocation("공연장", "busan_sea_festival"))
                .willThrow(new CustomException(ReportErrorResponseCode.REPORT_LOCATION_API_ERROR_502));

        long locationCount = reportLocationRepository.count();
        long reportCount = reportRepository.count();

        Report report = createReport("외부 API 오류 중 등록한 제보입니다.");

        assertThat(reportLocationRepository.count()).isEqualTo(locationCount + 1);
        assertThat(reportRepository.count()).isEqualTo(reportCount + 1);
        assertThat(reportRepository.existsById(report.getId())).isTrue();
        assertThat(reportLocationRepository.findById(report.getReportLocation().getId()).orElseThrow()
                .getSignatureName()).isNull();
    }

    @Test
    void rollsBackNewLocationWhenReportInsertFails() {
        long locationCount = reportLocationRepository.count();
        long reportCount = reportRepository.count();

        assertThatThrownBy(() -> createReport("가".repeat(501)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(reportLocationRepository.count()).isEqualTo(locationCount);
        assertThat(reportRepository.count()).isEqualTo(reportCount);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        verifyNoInteractions(festivalRepository, aiReportClient);
    }

    @Test
    void doesNotOverwriteNameFilledWhileWaitingForAi() {
        given(aiReportClient.getNormalizedLocation("공연장", "busan_sea_festival"))
                .willAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    ReportLocation location = reportLocationRepository.findActiveByFestival(
                            festival, LocalDateTime.now().minusMinutes(30)).getFirst();
                    assertThat(location.getSignatureName()).isNull();
                    transactionTemplate.executeWithoutResult(status ->
                            assertThat(reportLocationRepository.updateSignatureNameIfAbsent(
                                    location.getId(), "푸드트럭존")).isEqualTo(1));
                    return new AiNormalizedLocationRes("메인무대", true);
                });
        long locationCount = reportLocationRepository.count();

        Report report = createReport("AI 응답 전에 저장하는 제보입니다.");

        assertThat(reportLocationRepository.findById(report.getReportLocation().getId()).orElseThrow()
                .getSignatureName()).isEqualTo("푸드트럭존");
        assertThat(report.getOriginalLocation()).isEqualTo("공연장");
        assertThat(reportLocationRepository.count()).isEqualTo(locationCount + 1);
    }

    @Test
    void fillsUnnamedGroupUsingNextReportsOriginalLocation() {
        given(aiReportClient.getNormalizedLocation("공연장", "busan_sea_festival"))
                .willReturn(new AiNormalizedLocationRes(null, false));
        Report first = createReport("대표 이름이 없는 첫 제보입니다.");
        long locationCount = reportLocationRepository.count();
        given(aiReportClient.getNormalizedLocation("셔틀 타는 곳", "busan_sea_festival"))
                .willReturn(new AiNormalizedLocationRes("셔틀버스 탑승장", true));

        Report next = createReport("대표 이름을 채우는 다음 제보입니다.", "셔틀 타는 곳", 35.15338);

        assertThat(next.getReportLocation().getId()).isEqualTo(first.getReportLocation().getId());
        assertThat(reportLocationRepository.count()).isEqualTo(locationCount);
        ReportLocation location = reportLocationRepository.findById(first.getReportLocation().getId()).orElseThrow();
        assertThat(location.getSignatureName()).isEqualTo("셔틀버스 탑승장");
        assertThat(location.getAnchorLatitude()).isEqualTo(35.1532);
        assertThat(reportRepository.findById(first.getId()).orElseThrow().getOriginalLocation()).isEqualTo("공연장");
        assertThat(reportRepository.findById(next.getId()).orElseThrow().getOriginalLocation()).isEqualTo("셔틀 타는 곳");
    }

    @Test
    void keepsCommittedReportWhenNameUpdateFailsInDatabase() {
        given(aiReportClient.getNormalizedLocation("공연장", "busan_sea_festival"))
                .willReturn(new AiNormalizedLocationRes("가".repeat(256), true));

        Report report = createReport("대표 이름 저장 오류에도 유지되는 제보입니다.");

        assertThat(reportRepository.existsById(report.getId())).isTrue();
        assertThat(reportLocationRepository.findById(report.getReportLocation().getId()).orElseThrow()
                .getSignatureName()).isNull();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void includesUnnamedGroupInSummaryAndKeepsAddingReportsWhenNormalizationFails() {
        given(aiReportClient.getNormalizedLocation("공연장", "busan_sea_festival"))
                .willReturn(new AiNormalizedLocationRes(null, false));
        Report first = createReport("대표 이름이 없는 첫 제보입니다.");
        Report second = createReport("대표 이름이 없는 두 번째 제보입니다.");
        Report third = createReport("대표 이름이 없는 세 번째 제보입니다.");

        assertThat(second.getReportLocation().getId()).isEqualTo(first.getReportLocation().getId());
        assertThat(third.getReportLocation().getId()).isEqualTo(first.getReportLocation().getId());
        assertThat(reportService.getReportSummaryList(festival.getContentId(), 2026))
                .singleElement().satisfies(summary -> {
                    assertThat(summary.reportLocationId()).isEqualTo(first.getReportLocation().getId());
                    assertThat(summary.signatureName()).isNull();
                    assertThat(summary.reportCount()).isEqualTo(3);
                    assertThat(summary.representativeLevel()).isEqualTo(CongestionLevel.CROWDED);
                });
    }

    @Test
    void groupsBothSidesOfAnchorAndReturnsEachOriginalNameWithOneSummary() {
        Report center = createReport("기준 좌표에 등록한 제보입니다.");
        clearInvocations(festivalRepository, aiReportClient);

        Report first = createReport("기준 좌표 남쪽의 제보입니다.", "메인 스테이지", 35.15302);
        Report second = createReport("기준 좌표 북쪽의 제보입니다.", "  푸드트럭 앞  ", 35.15338);

        assertThat(first.getReportLocation().getId()).isEqualTo(center.getReportLocation().getId());
        assertThat(second.getReportLocation().getId()).isEqualTo(center.getReportLocation().getId());
        ReportLocation location = reportLocationRepository.findById(center.getReportLocation().getId()).orElseThrow();
        assertThat(location.getAnchorLatitude()).isEqualTo(35.1532);
        assertThat(location.getAnchorLongitude()).isEqualTo(129.1187);
        assertThat(location.getSignatureName()).isEqualTo("메인무대");

        var feed = reportService.getReportFeedList(null, festival.getContentId(), 2026, null, 10);
        assertThat(feed.getItems()).extracting(ReportFeedRes::originalLocation)
                .containsExactly("  푸드트럭 앞  ", "메인 스테이지", "공연장");
        assertThat(reportService.getReportSummaryList(festival.getContentId(), 2026))
                .singleElement().satisfies(summary -> {
                    assertThat(summary.reportLocationId()).isEqualTo(location.getId());
                    assertThat(summary.signatureName()).isEqualTo("메인무대");
                    assertThat(summary.reportCount()).isEqualTo(3);
                    assertThat(summary.representativeLevel()).isEqualTo(CongestionLevel.CROWDED);
                });
        verifyNoInteractions(festivalRepository, aiReportClient);
    }

    @Test
    void doesNotExtendAreaThroughAnIntermediateReport() {
        Report first = createReport("최초 기준 좌표에 등록한 제보입니다.");
        Report middle = createReport("최초 기준 좌표에서 약 20m 떨어진 제보입니다.", "공연장", 35.15338);
        Report last = createReport("최초 기준 좌표에서 약 40m 떨어진 제보입니다.", "공연장", 35.15356);

        assertThat(middle.getReportLocation().getId()).isEqualTo(first.getReportLocation().getId());
        assertThat(last.getReportLocation().getId()).isNotEqualTo(first.getReportLocation().getId());
        assertThat(reportLocationRepository.findById(first.getReportLocation().getId()).orElseThrow()
                .getAnchorLatitude()).isEqualTo(35.1532);
        assertThat(reportService.getReportSummaryList(festival.getContentId(), 2026))
                .hasSize(2).allSatisfy(summary -> {
                    assertThat(summary.signatureName()).isEqualTo("메인무대");
                    assertThat(summary.representativeLevel()).isNull();
                });
    }

    private Report createReport(String content) {
        return createReport(content, "공연장", 35.1532);
    }

    private Report createReport(String content, String originalLocation, double latitude) {
        return reportService.createReport(user.getId(), festival.getContentId(), 2026,
                new ReportCreateReq(latitude, 129.1187, content, CongestionLevel.CROWDED, null, originalLocation));
    }
}
