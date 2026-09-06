package com.api.fespot.domain.plan.web.controller;

import com.api.fespot.domain.plan.entity.VisitPlan;
import com.api.fespot.domain.plan.enums.PlanSuccessResponseCode;
import com.api.fespot.domain.plan.service.PlanService;
import com.api.fespot.domain.plan.service.dto.PlanListResult;
import com.api.fespot.domain.plan.web.dto.PlanDetailResponse;
import com.api.fespot.domain.plan.web.dto.PlanListResponse;
import com.api.fespot.domain.plan.web.dto.PlanRequest;
import com.api.fespot.domain.plan.web.dto.valid.Create;
import com.api.fespot.domain.plan.web.dto.valid.Update;
import com.api.fespot.global.response.CursorPage;
import com.api.fespot.global.response.SuccessResponse;
import com.api.fespot.global.security.AuthPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/my/plan")
public class PlanController {

    private final PlanService planService;

    @GetMapping
    public ResponseEntity<SuccessResponse<PlanListResponse>> getAllPlans(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @RequestParam(name = "cursor", required = false) Long cursor,
            @RequestParam(name = "size", defaultValue = "10") @Min(1) @Max(15) Integer size
    ) {
        PlanListResult result = planService.findAllVisitPlans(authPrincipal.getId(), cursor, size);
        List<PlanListResponse.PlanBlock> planBlocks = PlanListResponse.PlanBlock.convertToPlanBlocks(result.plans());

        PlanListResponse response = new PlanListResponse(
                result.totalCount(),
                CursorPage.of(planBlocks, result.nextCursor(), result.hasNext())
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.of(response, PlanSuccessResponseCode.PLAN_FIND_ALL_SUCCESS));
    }

    @GetMapping("/{planId}")
    public ResponseEntity<SuccessResponse<?>> getPlanDetail(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable Long planId
    ) {

        VisitPlan visitPlan = planService.findDetailVisitPlan(authPrincipal.getId(), planId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.of(PlanDetailResponse.of(visitPlan), PlanSuccessResponseCode.PLAN_FIND_DETAIL_SUCCESS));
    }

    @PostMapping
    public ResponseEntity<SuccessResponse<?>> createPlan(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @RequestBody @Validated(Create.class) PlanRequest request
            ) {

        VisitPlan visitPlan = planService.createVisitPlan(authPrincipal.getId(), request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(URI.create("/my/plan/" + visitPlan.getId()))
                .body(SuccessResponse.of(
                        PlanDetailResponse.of(visitPlan),
                        PlanSuccessResponseCode.PLAN_CREATE_SUCCESS
                ));
    }

    @PutMapping("/{planId}")
    public ResponseEntity<SuccessResponse<PlanDetailResponse>> updatePlan(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable @NotNull(message = "방문 계획 기본 키는 비어 있을 수 없습니다.") Long planId,
            @RequestBody @Validated(Update.class) PlanRequest request
    ) {
        VisitPlan visitPlan = planService.updateVisitPlan(authPrincipal.getId(), planId, request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .location(URI.create("/my/plan/" + visitPlan.getId()))
                .body(SuccessResponse.of(
                        PlanDetailResponse.of(visitPlan),
                        PlanSuccessResponseCode.PLAN_UPDATE_SUCCESS
                ));
    }

    @DeleteMapping("/{planId}")
    public ResponseEntity<SuccessResponse<Void>> deletePlan(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable @NotNull(message = "방문 계획 기본 키는 비어 있을 수 없습니다.") Long planId
    ) {
        planService.deleteVisitPlan(authPrincipal.getId(), planId);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .body(SuccessResponse.emptyCustom(PlanSuccessResponseCode.PLAN_UPDATE_SUCCESS.getMessage()));
    }
}
