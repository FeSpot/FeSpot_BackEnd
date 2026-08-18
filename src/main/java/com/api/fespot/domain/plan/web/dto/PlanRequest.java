package com.api.fespot.domain.plan.web.dto;

import com.api.fespot.domain.plan.entity.VisitPlace;
import com.api.fespot.domain.plan.entity.VisitPlan;
import com.api.fespot.domain.plan.web.dto.valid.Create;
import com.api.fespot.domain.plan.web.dto.valid.Update;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record PlanRequest(
        @NotBlank(message = "축제 식별자 필드는 비어 있을 수 없습니다.", groups = { Create.class, Update.class })
        String contentId,

        @NotNull(message = "축제 개최일 필드는 비어 있을 수 없습니다.", groups = { Create.class, Update.class })
        @Future(message = "이미 지난 축제는 해당 요청이 불가능합니다.", groups = { Create.class, Update.class })
        LocalDate startDate,

        @NotNull(message = "방문 일자 필드는 비어 있을 수 없습니다.", groups = { Create.class, Update.class })
        @FutureOrPresent(message = "방문 일자는 과거일 수 없습니다.", groups = { Create.class, Update.class })
        LocalDate targetDate,

        @Valid
        // 생성: null/빈 배열 모두 거부. 수정: null은 "장소 수정 없음"으로 허용, 빈 배열(전체 삭제)은 거부.
        @NotEmpty(message = "방문 계획 배열은 비어 있을 수 없습니다.", groups = Create.class)
        @Size(min = 1, message = "방문 계획 배열은 비어 있을 수 없습니다.", groups = Update.class)
        List<Plan> plans
) {
    public record Plan(
            Long placeId,

            @NotNull(message = "시작 시각은 비어 있을 수 없습니다.", groups = Create.class)
            LocalTime startAt,

            @NotBlank(message = "방문 장소의 이름은 비어 있을 수 없습니다.", groups = Create.class)
            String name
    ) {
        @AssertTrue(message = "방문 계획의 각 항목 시작 시각에 대해, 분 단위는 5의 배수여야 합니다.", groups = { Create.class, Update.class })
        public boolean isStartedAtIsMultiplesOfFive() {
            // 각 검증은 독립적임
            // 따라서 startedAt.getMinute() 호출에 NPE 발생을 막기 위해
            // null 값이어도 true를 반환케 함. null 검증은 위의 NotNull의 책임
            return startAt == null || startAt.getMinute() % 5 == 0;
        }

        public VisitPlace convertToVisitPlace(VisitPlan visitPlan) {
            return VisitPlace.builder()
                    .startAt(startAt())
                    .placeName(name())
                    .visitPlan(visitPlan)
                    .build();
        }
    }
}