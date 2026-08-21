package com.api.fespot.domain.report.web.controller;

import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.service.ReportService;
import com.api.fespot.domain.report.web.dto.ReportCreateReq;
import com.api.fespot.global.response.SuccessResponse;
import com.api.fespot.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Report", description = "제보 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

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
                request,
                null);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.create(report.getId()));
    }
}
