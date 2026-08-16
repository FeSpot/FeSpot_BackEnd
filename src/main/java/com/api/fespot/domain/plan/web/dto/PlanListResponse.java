package com.api.fespot.domain.plan.web.dto;

import com.api.fespot.domain.plan.entity.VisitPlace;
import com.api.fespot.domain.plan.entity.VisitPlan;
import com.api.fespot.global.response.CursorPage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record PlanListResponse(
        Integer totalCount,
        CursorPage<PlanBlock> plans
) {
    public record PlanBlock(
            String contentId,

            Long planId,

            String festivalName,
            LocalDate targetDate,
            List<PlaceBlock> places
    ) {
        public static List<PlanBlock> convertToPlanBlocks(List<VisitPlan> visitPlans) {

            List<PlanBlock> blocks = new ArrayList<>();

            for (VisitPlan visitPlan : visitPlans) {
                blocks.add(
                        new PlanBlock(
                                visitPlan.getFestival().getContentId(),
                                visitPlan.getId(),
                                // FIXME: 축제 이름
                                "",
                                visitPlan.getPlanTargetDate(),
                                visitPlan.getPlaces().stream()
                                        .sorted(Comparator.comparing(VisitPlace::getStartAt))
                                        .limit(4)
                                        .map(PlaceBlock::convertToPlaceBlock)
                                        .toList()
                ));
            }
            return blocks;
        }
    }
}
