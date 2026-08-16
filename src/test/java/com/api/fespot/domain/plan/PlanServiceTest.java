package com.api.fespot.domain.plan;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.plan.entity.VisitPlan;
import com.api.fespot.domain.plan.entity.enums.PlaceType;
import com.api.fespot.domain.plan.repository.VisitPlanRepository;
import com.api.fespot.domain.plan.service.PlanService;
import com.api.fespot.domain.plan.web.dto.PlanRequest;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.exception.UserErrorResponseCode;
import com.api.fespot.domain.user.repository.UserRepository;
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
    @Autowired private FestivalJpaRepository festivalJpaRepository;
    @Autowired private UserRepository userRepository;
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
                            "name",
                            PlaceType.CAFE)
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
        assertThat(found.getPlaces().get(0).getPlaceType()).isEqualTo(PlaceType.CAFE);
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
