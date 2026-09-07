package com.api.fespot.domain.plan.service.dto;

import com.api.fespot.domain.plan.entity.VisitPlan;

import java.util.List;
import java.util.Map;

public record PlanListResult (
    Integer totalCount,
    List<VisitPlan> plans,
    Map<String, String> festivalNameByContentId,
    Long nextCursor,
    boolean hasNext
) {
    public static PlanListResult empty(Integer totalCount) {
        return new PlanListResult(totalCount, List.of(), Map.of(), null, false);
    }
}
