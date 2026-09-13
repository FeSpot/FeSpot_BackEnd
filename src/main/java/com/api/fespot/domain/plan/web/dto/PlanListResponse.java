package com.api.fespot.domain.plan.web.dto;

import com.api.fespot.domain.plan.entity.VisitPlace;
import com.api.fespot.domain.plan.entity.VisitPlan;
import com.api.fespot.global.response.CursorPage;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public record PlanListResponse(
        Integer totalCount,
        CursorPage<PlanBlock> plans
) {
    public record PlanBlock(
            String contentId,

            Long planId,

            String festivalName,

            String planName,

            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "E MM.dd", locale = "ko")
            LocalDate targetDate,
            List<PlaceBlock> places
    ) {
        public static List<PlanBlock> convertToPlanBlocks(List<VisitPlan> visitPlans, Map<String, String> festivalNameByContentId) {

            return visitPlans.stream()
                    .map(plan -> new PlanBlock(
                            plan.getFestival().getContentId(),
                            plan.getId(),
                            festivalNameByContentId.get(plan.getFestival().getContentId()),
                            plan.getTitle(),
                            plan.getPlanTargetDate(),
                            summarizePlaces(plan.getPlaces())
                    )).toList();
        }

        /**
         * @return 방문 계획 내의 경유지가 3개를 초과할 시, 첫 2개와 마지막 1개를 반환
         */
        private static List<PlaceBlock> summarizePlaces(List<VisitPlace> places) {
            List<VisitPlace> sortedPlaces = places.stream()
                    .sorted(Comparator.comparing(VisitPlace::getStartAt))
                    .toList();

            if (sortedPlaces.size() <= 3) {
                return sortedPlaces.stream()
                        .map(PlaceBlock::convertToPlaceBlock)
                        .toList();
            }

            return Stream.of(sortedPlaces.get(0), sortedPlaces.get(1), sortedPlaces.get(sortedPlaces.size() - 1))
                    .map(PlaceBlock::convertToPlaceBlock)
                    .toList();
        }
    }
}
