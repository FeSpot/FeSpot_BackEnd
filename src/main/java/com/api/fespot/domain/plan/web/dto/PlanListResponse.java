package com.api.fespot.domain.plan.web.dto;


import com.api.fespot.domain.plan.entity.VisitPlan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
                                        .map(PlaceBlock::convertToPlaceBlock)
                                        .toList()
                ));
            }
            return blocks;
        }
    }
}
