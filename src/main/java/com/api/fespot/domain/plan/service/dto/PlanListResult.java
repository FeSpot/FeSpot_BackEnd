package com.api.fespot.domain.plan.service.dto;

import com.api.fespot.domain.plan.entity.VisitPlan;

import java.util.List;

public record PlanListResult (
    Integer totalCount,
    List<VisitPlan> plans,
    Long nextCursor,
    boolean hasNext
) {
    public static PlanListResult empty(Integer totalCount) {
        return new PlanListResult(totalCount, List.of(), null, false);
    }
}
