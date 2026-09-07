package com.api.fespot.domain.plan.web.dto;

import com.api.fespot.domain.plan.entity.VisitPlan;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

public record PlanDetailResponse(
        String contentId,

        Long planId,

        String planName,

        @JsonFormat(shape = JsonFormat.Shape.STRING, locale = "ko", pattern = "E MM.dd")
        LocalDate targetDate,
        List<PlaceBlock> places
) {
    public static PlanDetailResponse of(VisitPlan visitPlan) {
        return new PlanDetailResponse(
                visitPlan.getFestival().getContentId(),
                visitPlan.getId(),
                visitPlan.getTitle(),
                visitPlan.getPlanTargetDate(),
                visitPlan.getPlaces().stream()
                        .map(PlaceBlock::convertToPlaceBlock)
                        .toList()
        );
    }
}
