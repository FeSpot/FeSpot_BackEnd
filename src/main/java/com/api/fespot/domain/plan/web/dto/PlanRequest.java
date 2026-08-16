package com.api.fespot.domain.plan.web.dto;

import com.api.fespot.domain.plan.web.dto.valid.Create;
import com.api.fespot.domain.plan.web.dto.valid.Update;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

public record PlanRequest(
        @NotBlank(message = "축제 식별자 필드는 비어 있을 수 없습니다.", groups = { Create.class, Update.class })
        String festivalKey,     // 축제 목록 조회 - festivalKey (ex.BUSAN_FIREWORKS_FESTIVAL)

        @Valid
        @NotEmpty(message = "방문 계획 배열은 비어 있을 수 없습니다.", groups = Create.class)
        List<Plan> plans
) {
    public record Plan(
            @NotNull(message = "계획 식별자 필드는 비어있을 수 없습니다.", groups = Update.class)
            Long placeId,

            @NotNull(message = "시작 시각은 비어 있을 수 없습니다.", groups = Create.class)
            LocalTime startedAt,

            @NotBlank(message = "방문 장소의 이름은 비어 있을 수 없습니다.", groups = Create.class)
            String name
    ) {
        @AssertTrue(message = "방문 계획의 각 항목 시작 시각에 대해, 분 단위는 5의 배수여야 합니다.", groups = { Create.class, Update.class })
        public boolean isStartedAtIsMultiplesOfFive() {
            // 각 검증은 독립적임
            // 따라서 startedAt.getMinute() 호출에 NPE 발생을 막기 위해
            // null 값이어도 true를 반환케 함. null 검증은 위의 NotNull의 책임
            return startedAt == null || startedAt.getMinute() % 5 == 0;
        }
    }
}