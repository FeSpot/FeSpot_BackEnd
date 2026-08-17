package com.api.fespot.domain.plan;

import com.api.fespot.domain.plan.web.dto.PlanRequest;
import com.api.fespot.domain.plan.web.dto.valid.Create;
import com.api.fespot.domain.plan.web.dto.valid.Update;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class PlanDtoValidationTest {

    private Validator validator;

    private String festivalKey = "dummy";
    private LocalDate startDate = LocalDate.now().plusDays(2);
    private LocalDate targetDate = LocalDate.now().plusDays(1);
    private LocalDate pastDate = LocalDate.now().minusDays(1);
    private LocalTime time = LocalTime.of(0, 0);
    private String name = "name";

    private PlanRequest.Plan allValidatedPlan = new PlanRequest.Plan(1L, time, name);
    private PlanRequest.Plan placeKeyIsNull = new PlanRequest.Plan(null, time, name);
    private PlanRequest.Plan timeIsNull = new PlanRequest.Plan(1L, null, name);
    private PlanRequest.Plan isNotMul5 = new PlanRequest.Plan(1L, LocalTime.of(0, 1), name);
    private PlanRequest.Plan nameIsNull = new PlanRequest.Plan(1L, time, null);
    private PlanRequest.Plan nameIsBlank = new PlanRequest.Plan(1L, time, "");


    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("생성 요청에 축제 식별자 필드를 null값으로 넘기면 실패한다.")
    void isFestivalKeyIsRequiredInCreate() {

        // given
        PlanRequest request = new PlanRequest(null, startDate, targetDate, List.of(allValidatedPlan));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Create.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("수정 요청에 축제 식별자 필드를 null값으로 넘기면 실패한다.")
    void isFestivalKeyIsRequiredInUpdate() {

        // given
        PlanRequest request = new PlanRequest(null, startDate, targetDate, List.of(allValidatedPlan));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Update.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("생성 요청에 축제 식별자 필드를 빈 문자열로 넘기면 실패한다.")
    void isNotBlankFestivalKeyInCreate() {

        // given
        PlanRequest request = new PlanRequest("", startDate, targetDate, List.of(allValidatedPlan));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Create.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("수정 요청에 축제 식별자 필드를 빈 문자열로 넘기면 실패한다.")
    void isNotBlankFestivalKeyInUpdate() {

        // given
        PlanRequest request = new PlanRequest("", startDate, targetDate, List.of(allValidatedPlan));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Update.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("생성 요청에 축제 개최일 필드를 null값으로 넘기면 실패한다.")
    void isStartDateIsRequiredInCreate() {

        // given
        PlanRequest request = new PlanRequest(festivalKey, null, targetDate, List.of(allValidatedPlan));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Create.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("수정 요청에 축제 개최일 필드를 null값으로 넘기면 실패한다.")
    void isStartDateIsRequiredInUpdate() {

        // given
        PlanRequest request = new PlanRequest(festivalKey, null, targetDate, List.of(allValidatedPlan));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Update.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("생성 요청에 축제 개최일 필드를 과거 날짜로 넘기면 실패한다.")
    void isFutureStartDateInCreate() {

        // given
        PlanRequest request = new PlanRequest(festivalKey, pastDate, targetDate, List.of(allValidatedPlan));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Create.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("수정 요청에 축제 개최일 필드를 과거 날짜로 넘기면 실패한다.")
    void isFutureStartDateInUpdate() {

        // given
        PlanRequest request = new PlanRequest(festivalKey, pastDate, targetDate, List.of(allValidatedPlan));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Update.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("생성 요청에 방문 일자 필드를 null값으로 넘기면 실패한다.")
    void isTargetDateIsRequiredInCreate() {

        // given
        PlanRequest request = new PlanRequest(festivalKey, startDate, null, List.of(allValidatedPlan));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Create.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("수정 요청에 방문 일자 필드를 null값으로 넘기면 실패한다.")
    void isTargetDateIsRequiredInUpdate() {

        // given
        PlanRequest request = new PlanRequest(festivalKey, startDate, null, List.of(allValidatedPlan));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Update.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("생성 요청에 방문 일자 필드를 과거 날짜로 넘기면 실패한다.")
    void isFutureTargetDateInCreate() {

        // given
        PlanRequest request = new PlanRequest(festivalKey, startDate, pastDate, List.of(allValidatedPlan));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Create.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("수정 요청에 방문 일자 필드를 과거 날짜로 넘기면 실패한다.")
    void isFutureTargetDateInUpdate() {

        // given
        PlanRequest request = new PlanRequest(festivalKey, startDate, pastDate, List.of(allValidatedPlan));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Update.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("시작 시각이 null인 계획으로 생성 요청 시 실패한다.")
    void isNotNullStartedAtInPlanAtCalledCreateRequest() {
        // given
        PlanRequest request = new PlanRequest(festivalKey, startDate, targetDate, List.of(timeIsNull));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Create.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("장소 이름이 null인 계획으로 생성 요청 시 실패한다.")
    void isNotNullNameInPlanAtCalledCreateRequest() {
        // given
        PlanRequest request = new PlanRequest(festivalKey, startDate, targetDate, List.of(nameIsNull));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Create.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("장소 이름이 null인 계획으로 생성 요청 시 실패한다.")
    void isNotBlankNameInPlanAtCalledCreateRequest() {
        // given
        PlanRequest request = new PlanRequest(festivalKey, startDate, targetDate, List.of(nameIsBlank));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Create.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("방문 장소 식별자 필드가 null인 항목(신규 추가)이 포함된 수정 요청은 유효하다.")
    void isValidUpdateRequestWithNullPlaceKey() {
        // given
        PlanRequest request = new PlanRequest(festivalKey, startDate, targetDate, List.of(placeKeyIsNull));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Update.class);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("빈 계획 배열로 생성 요청 시 실패한다")
    void isNotEmptyPlansAtCalledCreateRequest() {
        // given
        PlanRequest request = new PlanRequest(festivalKey, startDate, targetDate, new ArrayList<>());

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Create.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("빈 계획 배열로 수정 요청 시 실패한다")
    void isNotEmptyPlansAtCalledUpdateRequest() {
        // given
        PlanRequest request = new PlanRequest(festivalKey, startDate, targetDate, new ArrayList<>());

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Update.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("계획 단일 항목 시작 시각이 5분 단위가 아니라면 실패한다.")
    void isNotMultipleOfFiveAboutStartedAt() {
        // given
        PlanRequest request = new PlanRequest(festivalKey, startDate, targetDate, List.of(isNotMul5));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Create.class);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("모든 값이 존재하는 정상적인 생성 요청")
    void isValidPlanCreateRequest() {
        // given
        PlanRequest request = new PlanRequest(festivalKey, startDate, targetDate, List.of(allValidatedPlan));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Create.class);

        // then
        assertThat(violations).hasSize(0);
    }

    @Test
    @DisplayName("방문 장소 식별자 필드가 null인 정상 생성 요청")
    void isValidPlanCreateRequestButPlaceIdIsNull() {
        // given
        PlanRequest request = new PlanRequest(festivalKey, startDate, targetDate, List.of(placeKeyIsNull));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Create.class);

        // then
        assertThat(violations).hasSize(0);
    }

    @Test
    @DisplayName("모든 값이 존재하는 정상적인 수정 요청")
    void isValidPlanUpdateRequest () {
        // given
        PlanRequest request = new PlanRequest(festivalKey, startDate, targetDate, List.of(allValidatedPlan));

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Update.class);

        // then
        assertThat(violations).hasSize(0);
    }

    @Test
    @DisplayName("방문 계획 수정사항이 없는 수정 요청")
    void isValidPlanUpdateRequestButPlacesIsNull() {
        // given
        PlanRequest request = new PlanRequest(festivalKey, startDate, targetDate, null);

        // when
        Set<ConstraintViolation<PlanRequest>> violations = validator.validate(request, Update.class);

        // then
        assertThat(violations).hasSize(0);
    }
}
