package com.api.fespot.domain.report.web.controller;

import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.service.ReportLikeService;
import com.api.fespot.domain.report.service.ReportService;
import com.api.fespot.domain.report.web.dto.*;
import com.api.fespot.global.response.CursorPage;
import com.api.fespot.global.response.SuccessResponse;
import com.api.fespot.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Report", description = "제보 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportLikeService reportLikeService;

    @Operation(summary = "제보 등록")
    @PostMapping
    public ResponseEntity<SuccessResponse<Long>> createReport(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @RequestParam
            @Pattern(regexp = "\\d+", message = "contentId는 숫자여야 합니다.")
            @Size(max = 64, message = "contentId는 64자 이하여야 합니다.") String contentId,
            @RequestParam
            @Min(value = 2026, message = "year는 2026 이상이어야 합니다.")
            @Max(value = 9999, message = "year는 네 자리 연도여야 합니다.") int year,
            @Valid @RequestBody ReportCreateReq request
    ) {
        Report report = reportService.createReport(
                authPrincipal.getId(),
                contentId,
                year,
                request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.create(report.getId()));
    }

    @Operation(summary = "나의 제보 조회")
    @GetMapping("/my")
    public ResponseEntity<SuccessResponse<CursorPage<MyReportRes>>> getMyReports(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,

            @RequestParam(required = false)
            @Positive(message = "cursor는 양수여야 합니다")
            Long cursor,

            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size는 1 이상이어야 합니다")
            @Max(value = 15, message = "size는 15이하여야 합니다")
            int size
    ) {
        CursorPage<MyReportRes> response = reportService.getMyReports(authPrincipal.getId(), cursor, size);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.ok(response));
    }

    @Operation(summary = "나의 제보 수정")
    @PatchMapping("/my/{reportId}")
    public ResponseEntity<SuccessResponse<?>> updateMyReports(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable Long reportId,
            @Valid @RequestBody UpdateMyReportReq req
    ) {
        reportService.updateMyReport(authPrincipal.getId(), reportId, req);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.emptyCustom("수정되었습니다."));
    }

    @Operation(summary = "나의 제보 삭제")
    @DeleteMapping("/my/{reportId}")
    public ResponseEntity<SuccessResponse<?>> deleteMyReports(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable Long reportId
    ) {
        reportService.deleteMyReport(authPrincipal.getId(), reportId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.emptyCustom("삭제되었습니다."));
    }

    @Operation(summary = "제보 피드 조회")
    @GetMapping("/feed")
    public ResponseEntity<SuccessResponse<CursorPage<ReportFeedRes>>> getReportFeed(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,

            @RequestParam
            @Pattern(regexp = "\\d+", message = "contentId는 숫자여야 합니다.")
            @Size(max = 64, message = "contentId는 64자 이하여야 합니다.") String contentId,

            @RequestParam(required = false)
            @Positive
            Long reportLocationId,

            @RequestParam
            @Min(value = 2026, message = "year는 2026 이상이어야 합니다.")
            @Max(value = 9999, message = "year는 네 자리 연도여야 합니다.") int year,

            @RequestParam(required = false)
            @Positive(message = "cursor는 양수여야 합니다")
            Long cursor,

            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size는 1 이상이어야 합니다")
            @Max(value = 15, message = "size는 15이하여야 합니다")
            int size
    ) {
        Long userId = authPrincipal == null ? null : authPrincipal.getId();
        CursorPage<ReportFeedRes> response = reportService.getReportFeedList(userId, contentId, reportLocationId, year, cursor, size);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.ok(response));
    }

    @Operation(summary = "제보 요약 조회")
    @GetMapping("/summary")
    public ResponseEntity<SuccessResponse<?>> getReportSummary(

            @RequestParam
            @Pattern(regexp = "\\d+", message = "contentId는 숫자여야 합니다.")
            @Size(max = 64, message = "contentId는 64자 이하여야 합니다.") String contentId,

            @RequestParam
            @Min(value = 2026, message = "year는 2026 이상이어야 합니다.")
            @Max(value = 9999, message = "year는 네 자리 연도여야 합니다.") int year
    ) {
        List<ReportSummaryRes> response = reportService.getReportSummaryList(contentId, year);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.ok(response));
    }


    @Operation(summary = "사용자가 제보피드에 좋아요 설정")
    @PutMapping("/{reportId}/like")
    public ResponseEntity<SuccessResponse<?>> updateReportLike(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @PathVariable Long reportId,
            @Valid @RequestBody ReportLikeReq req
    ) {
        reportLikeService.updateReportLike(authPrincipal.getId(), reportId, req.liked());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.emptyCustom("좋아요 변경 완료"));
    }
}
