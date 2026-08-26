package com.api.fespot.domain.plan;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.plan.entity.VisitPlace;
import com.api.fespot.domain.plan.entity.VisitPlan;
import com.api.fespot.domain.plan.repository.VisitPlaceRepository;
import com.api.fespot.domain.plan.repository.VisitPlanRepository;
import com.api.fespot.domain.plan.service.PlanService;
import com.api.fespot.domain.plan.web.dto.PlanRequest;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.exception.UserErrorResponseCode;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.domain.plan.service.dto.PlanListResult;
import com.api.fespot.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PlanService.class)
@Testcontainers(disabledWithoutDocker = true)
public class PlanServiceTest {

    @Container
    @ServiceConnection
    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired private PlanService planService;
    @Autowired private VisitPlanRepository visitPlanRepository;
    @Autowired private VisitPlaceRepository visitPlaceRepository;
    @Autowired private TestEntityManager entityManager;

    private Festival festival;
    private User user;

    private PlanRequest planRequest = new PlanRequest(
            "1",
            LocalDate.now().plusDays(2),
            LocalDate.now().plusDays(1),
            List.of(
                    new PlanRequest.Plan(1L,
                            LocalTime.of(0, 0),
                            "name")
            )
    );

    @BeforeEach
    void setUp() {
        festival = entityManager.persist(Festival.create("1", LocalDate.now().plusDays(2).getYear()));
        user = entityManager.persist(User.create("plan-test@fespot.com", "passwordHash"));
    }

    @Test
    @DisplayName("PlanService.createVisitPlan은 만들어진 VisitPlan을 반환한다.")
    void testCreatePlan() {
        // when
        VisitPlan visitPlan = planService.createVisitPlan(user.getId(), planRequest);

        // then
        assertThat(visitPlan.getId()).isNotNull();
        assertThat(visitPlan.getPlanTargetDate()).isEqualTo(planRequest.targetDate());
        assertThat(visitPlan.getUser()).isEqualTo(user);
        assertThat(visitPlan.getFestival()).isEqualTo(festival);
    }

    @Test
    @DisplayName("계획 생성 시 방문 장소들이 VisitPlan과 연결되어 저장된다.")
    void testCreatePlanPersistsVisitPlaces() {
        // when
        VisitPlan visitPlan = planService.createVisitPlan(user.getId(), planRequest);
        entityManager.flush();
        entityManager.clear();

        // then
        VisitPlan found = visitPlanRepository.findById(visitPlan.getId()).orElseThrow();
        assertThat(found.getPlaces()).hasSize(1);
        assertThat(found.getPlaces().get(0).getPlaceName()).isEqualTo("name");
        assertThat(found.getPlaces().get(0).getStartAt()).isEqualTo(LocalTime.of(0, 0));
    }

    @Test
    @DisplayName("사용자의 모든 계획을 방문 장소까지 포함해 조회한다.")
    void testFindAllVisitPlans() {
        // given
        planService.createVisitPlan(user.getId(), planRequest);

        PlanRequest secondRequest = new PlanRequest(
                "1",
                planRequest.startDate(),
                planRequest.targetDate().plusDays(1),
                List.of(new PlanRequest.Plan(null, LocalTime.of(15, 0), "secondPlace"))
        );
        planService.createVisitPlan(user.getId(), secondRequest);
        entityManager.flush();
        entityManager.clear();

        // when
        PlanListResult result = planService.findAllVisitPlans(user.getId(), null, 10);

        // then
        assertThat(result.plans()).hasSize(2);
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.plans()).allSatisfy(plan -> assertThat(plan.getPlaces()).hasSize(1));
        assertThat(result.plans()).flatExtracting(VisitPlan::getPlaces)
                .extracting(VisitPlace::getPlaceName)
                .containsExactlyInAnyOrder("name", "secondPlace");
    }

    @Test
    @DisplayName("계획이 size보다 많으면 커서로 다음 페이지를 조회할 수 있다.")
    void testFindAllVisitPlansWithCursorPaging() {
        // given - 계획 3개 생성
        VisitPlan first = planService.createVisitPlan(user.getId(), planRequest);
        VisitPlan second = planService.createVisitPlan(user.getId(), new PlanRequest(
                "1",
                planRequest.startDate(),
                planRequest.targetDate().plusDays(1),
                List.of(new PlanRequest.Plan(null, LocalTime.of(15, 0), "secondPlace"))
        ));
        VisitPlan third = planService.createVisitPlan(user.getId(), new PlanRequest(
                "1",
                planRequest.startDate(),
                planRequest.targetDate().plusDays(2),
                List.of(new PlanRequest.Plan(null, LocalTime.of(17, 0), "thirdPlace"))
        ));
        entityManager.flush();
        entityManager.clear();

        // when - 첫 페이지 (최신순, size 2)
        PlanListResult firstPage = planService.findAllVisitPlans(user.getId(), null, 2);

        // then
        assertThat(firstPage.plans()).extracting(VisitPlan::getId)
                .containsExactly(third.getId(), second.getId());
        assertThat(firstPage.totalCount()).isEqualTo(3);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.nextCursor()).isEqualTo(second.getId());

        // when - 커서로 두 번째 페이지
        PlanListResult secondPage =
                planService.findAllVisitPlans(user.getId(), firstPage.nextCursor(), 2);

        // then
        assertThat(secondPage.plans()).extracting(VisitPlan::getId)
                .containsExactly(first.getId());
        assertThat(secondPage.totalCount()).isEqualTo(3);
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    @DisplayName("다른 사용자의 계획은 전체 조회 결과에 포함되지 않는다.")
    void testFindAllVisitPlansExcludesOtherUsersPlans() {
        // given
        VisitPlan mine = planService.createVisitPlan(user.getId(), planRequest);

        User otherUser = entityManager.persist(User.create("other-user-list@fespot.com", "passwordHash"));
        PlanRequest otherRequest = new PlanRequest(
                "1",
                planRequest.startDate(),
                planRequest.targetDate(),
                List.of(new PlanRequest.Plan(null, LocalTime.of(16, 0), "otherPlace"))
        );
        planService.createVisitPlan(otherUser.getId(), otherRequest);
        entityManager.flush();
        entityManager.clear();

        // when
        PlanListResult result = planService.findAllVisitPlans(user.getId(), null, 10);

        // then
        assertThat(result.plans()).hasSize(1);
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.plans().get(0).getId()).isEqualTo(mine.getId());
    }

    @Test
    @DisplayName("계획이 없는 사용자는 빈 리스트를 반환한다.")
    void testFindAllVisitPlansReturnsEmptyList() {
        // when
        PlanListResult result = planService.findAllVisitPlans(user.getId(), null, 10);

        // then
        assertThat(result.plans()).isEmpty();
        assertThat(result.totalCount()).isEqualTo(0);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("계획 상세 조회 시 방문 장소까지 포함한 VisitPlan을 반환한다.")
    void testFindDetailVisitPlan() {
        // given
        VisitPlan created = planService.createVisitPlan(user.getId(), planRequest);
        entityManager.flush();
        entityManager.clear();

        // when
        VisitPlan found = planService.findDetailVisitPlan(user.getId(), created.getId());

        // then
        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getPlanTargetDate()).isEqualTo(planRequest.targetDate());
        assertThat(found.getPlaces()).hasSize(1);
        assertThat(found.getPlaces().get(0).getPlaceName()).isEqualTo("name");
        assertThat(found.getPlaces().get(0).getStartAt()).isEqualTo(LocalTime.of(0, 0));
    }

    @Test
    @DisplayName("존재하지 않는 계획을 상세 조회하면 예외를 던진다.")
    void testFindDetailVisitPlanWithNotExistPlan() {
        // when & then
        assertThatThrownBy(() -> planService.findDetailVisitPlan(user.getId(), 999_999L))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("다른 사용자의 계획은 상세 조회할 수 없다.")
    void testFindDetailVisitPlanOfOtherUser() {
        // given
        VisitPlan created = planService.createVisitPlan(user.getId(), planRequest);
        User otherUser = entityManager.persist(User.create("other-user@fespot.com", "passwordHash"));
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> planService.findDetailVisitPlan(otherUser.getId(), created.getId()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("계획 수정 시 plans가 null이면 방문 장소는 유지하고 방문 일자만 변경한다.")
    void testUpdatePlanWithNullPlansKeepsPlaces() {
        // given
        VisitPlan created = planService.createVisitPlan(user.getId(), planRequest);
        entityManager.flush();
        entityManager.clear();

        LocalDate newTargetDate = LocalDate.now().plusDays(3);
        PlanRequest updateRequest = new PlanRequest(
                "1",
                planRequest.startDate(),
                newTargetDate,
                null
        );

        // when
        planService.updateVisitPlan(user.getId(), created.getId(), updateRequest);
        entityManager.flush();
        entityManager.clear();

        // then
        VisitPlan found = visitPlanRepository.findById(created.getId()).orElseThrow();
        assertThat(found.getPlanTargetDate()).isEqualTo(newTargetDate);
        assertThat(found.getPlaces()).hasSize(1);
        assertThat(found.getPlaces().get(0).getPlaceName()).isEqualTo("name");
    }

    @Test
    @DisplayName("계획 수정 시 식별자가 있는 방문 장소는 요청 값으로 수정된다.")
    void testUpdatePlanModifiesExistingPlace() {
        // given
        VisitPlan created = planService.createVisitPlan(user.getId(), planRequest);
        Long placeId = created.getPlaces().get(0).getId();
        entityManager.flush();
        entityManager.clear();

        PlanRequest updateRequest = new PlanRequest(
                "1",
                planRequest.startDate(),
                planRequest.targetDate(),
                List.of(new PlanRequest.Plan(placeId, LocalTime.of(11, 30), "updatedName"))
        );

        // when
        planService.updateVisitPlan(user.getId(), created.getId(), updateRequest);
        entityManager.flush();
        entityManager.clear();

        // then
        VisitPlan found = visitPlanRepository.findById(created.getId()).orElseThrow();
        assertThat(found.getPlaces()).hasSize(1);

        VisitPlace place = found.getPlaces().get(0);
        assertThat(place.getId()).isEqualTo(placeId);
        assertThat(place.getPlaceName()).isEqualTo("updatedName");
        assertThat(place.getStartAt()).isEqualTo(LocalTime.of(11, 30));
    }

    @Test
    @DisplayName("계획 수정 시 식별자가 없는 방문 장소는 새로 저장된다.")
    void testUpdatePlanSavesNewPlace() {
        // given
        VisitPlan created = planService.createVisitPlan(user.getId(), planRequest);
        Long placeId = created.getPlaces().get(0).getId();
        entityManager.flush();
        entityManager.clear();

        PlanRequest updateRequest = new PlanRequest(
                "1",
                planRequest.startDate(),
                planRequest.targetDate(),
                List.of(
                        new PlanRequest.Plan(placeId, LocalTime.of(11, 30), "updatedName"),
                        new PlanRequest.Plan(null, LocalTime.of(13, 0), "newPlace")
                )
        );

        // when
        planService.updateVisitPlan(user.getId(), created.getId(), updateRequest);
        entityManager.flush();
        entityManager.clear();

        // then
        VisitPlan found = visitPlanRepository.findById(created.getId()).orElseThrow();
        assertThat(found.getPlaces()).hasSize(2);
        assertThat(found.getPlaces()).extracting(VisitPlace::getPlaceName)
                .containsExactlyInAnyOrder("updatedName", "newPlace");

        VisitPlace newPlace = found.getPlaces().stream()
                .filter(place -> place.getPlaceName().equals("newPlace"))
                .findFirst().orElseThrow();
        assertThat(newPlace.getId()).isNotNull();
        assertThat(newPlace.getStartAt()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    @DisplayName("방문 장소가 2개 이상인 계획도 수정할 수 있다.")
    void testUpdatePlanWithMultiplePlaces() {
        // given
        PlanRequest createRequest = new PlanRequest(
                "1",
                planRequest.startDate(),
                planRequest.targetDate(),
                List.of(
                        new PlanRequest.Plan(null, LocalTime.of(10, 0), "placeA"),
                        new PlanRequest.Plan(null, LocalTime.of(12, 0), "placeB")
                )
        );
        VisitPlan created = planService.createVisitPlan(user.getId(), createRequest);
        Long placeAId = created.getPlaces().get(0).getId();
        Long placeBId = created.getPlaces().get(1).getId();
        entityManager.flush();
        entityManager.clear();

        PlanRequest updateRequest = new PlanRequest(
                "1",
                planRequest.startDate(),
                planRequest.targetDate(),
                List.of(
                        new PlanRequest.Plan(placeAId, LocalTime.of(10, 30), "placeA2"),
                        new PlanRequest.Plan(placeBId, LocalTime.of(12, 30), "placeB2")
                )
        );

        // when
        planService.updateVisitPlan(user.getId(), created.getId(), updateRequest);
        entityManager.flush();
        entityManager.clear();

        // then
        VisitPlan found = visitPlanRepository.findById(created.getId()).orElseThrow();
        assertThat(found.getPlaces()).hasSize(2);
        assertThat(found.getPlaces()).extracting(VisitPlace::getPlaceName)
                .containsExactlyInAnyOrder("placeA2", "placeB2");
    }

    @Test
    @DisplayName("계획 삭제 시 방문 장소들도 함께 삭제된다.")
    void testDeleteVisitPlan() {
        // given
        VisitPlan created = planService.createVisitPlan(user.getId(), planRequest);
        Long placeId = created.getPlaces().get(0).getId();
        entityManager.flush();
        entityManager.clear();

        // when
        planService.deleteVisitPlan(user.getId(), created.getId());
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(visitPlanRepository.findById(created.getId())).isEmpty();
        assertThat(visitPlaceRepository.findById(placeId)).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 계획을 삭제하면 예외를 던진다.")
    void testDeleteVisitPlanWithNotExistPlan() {
        // when & then
        assertThatThrownBy(() -> planService.deleteVisitPlan(user.getId(), 999_999L))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("다른 사용자의 계획은 삭제할 수 없다.")
    void testDeleteVisitPlanOfOtherUser() {
        // given
        VisitPlan created = planService.createVisitPlan(user.getId(), planRequest);
        User otherUser = entityManager.persist(User.create("other-user-delete@fespot.com", "passwordHash"));
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> planService.deleteVisitPlan(otherUser.getId(), created.getId()))
                .isInstanceOf(RuntimeException.class);
        assertThat(visitPlanRepository.findById(created.getId())).isPresent();
    }

    @Test
    @DisplayName("존재하지 않는 축제로 계획 생성 요청 시 FESTIVAL_NOT_FOUND_404 예외를 던진다.")
    void testCreatePlanWithNotExistFestival() {
        // given
        PlanRequest notExistFestivalRequest = new PlanRequest(
                "not-exist",
                planRequest.startDate(),
                planRequest.targetDate(),
                planRequest.plans()
        );

        // when & then
        assertThatThrownBy(() -> planService.createVisitPlan(user.getId(), notExistFestivalRequest))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404);
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 계획 생성 요청 시 USER_NOT_FOUND_404 예외를 던진다.")
    void testCreatePlanWithNotExistUser() {
        // when & then
        assertThatThrownBy(() -> planService.createVisitPlan(user.getId() + 999L, planRequest))
                .isInstanceOf(CustomException.class)
                .extracting("baseResponseCode")
                .isEqualTo(UserErrorResponseCode.USER_NOT_FOUND_404);
    }
}
