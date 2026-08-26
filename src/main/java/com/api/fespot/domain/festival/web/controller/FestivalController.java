package com.api.fespot.domain.festival.web.controller;

import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastResponse;
import com.api.fespot.domain.festival.entity.FestivalKey;
import com.api.fespot.domain.festival.service.FestivalLikeService;
import com.api.fespot.domain.festival.service.FestivalNotificationService;
import com.api.fespot.domain.festival.service.FestivalService;
import com.api.fespot.domain.festival.web.dto.*;
import com.api.fespot.global.response.SuccessResponse;
import com.api.fespot.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Festival", description = "축제 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/festivals")
public class FestivalController {

    private final FestivalService festivalService;
    private final FestivalLikeService festivalLikeService;
    private final FestivalNotificationService festivalNotificationService;

    @Operation(summary = "홈 고정 축제 조회")
    @SecurityRequirements
    @GetMapping("/home")
    public ResponseEntity<SuccessResponse<List<FestivalHomeRes>>> getHomeFestivals(
            @RequestParam(defaultValue = "2026")
            @Min(value = 2026, message = "year는 2026 이상이어야 합니다.")
            @Max(value = 9999, message = "year는 네 자리 연도여야 합니다.") int year) {
        List<FestivalHomeRes> response = festivalService.getHomeFestivals(year);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.ok(response));
    }

    @Operation(summary = "축제 상세 기본정보 조회")
    @SecurityRequirements
    @GetMapping("/{contentId}")
    public ResponseEntity<SuccessResponse<FestivalDetailRes>> getFestivalDetail(
            @PathVariable
            @Pattern(regexp = "\\d+", message = "contentId는 숫자여야 합니다.") String contentId
    ) {
        FestivalDetailRes response = festivalService.getFestivalDetail(contentId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.ok(response));
    }

    @Operation(summary = "날짜&시간별 축제 혼잡도 예측 조회")
    @SecurityRequirements
    @GetMapping("/{festivalKey}/daily-forecast")
    public ResponseEntity<SuccessResponse<AiDailyFestivalCongestionForecastResponse>>
    getDailyFestivalCongestionForecast(
            @PathVariable FestivalKey festivalKey,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        AiDailyFestivalCongestionForecastResponse response =
                festivalService.getDailyFestivalCongestionForecast(
                        festivalKey,
                        date);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.ok(response));
    }

    @Operation(summary = "반경 1km 주변 시설, 혼잡도 예측 조회")
    @SecurityRequirements
    @GetMapping("{festivalKey}/area-forecast")
    public ResponseEntity<SuccessResponse<FestivalAreaCongestionForecastRes>>
    getFestivalAreaCongestionForecast(
            @PathVariable FestivalKey festivalKey,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Pattern(
                    regexp = "^(?:0[8-9]|1[0-9]|2[0-3]):00$",
                    message = "time은 08:00~23:00 범위의 정각이어야 합니다."
            )
            @RequestParam
            String time
    ) {
        FestivalAreaCongestionForecastRes response = festivalService.getFestivalAreaCongestionForecast(festivalKey, date, LocalTime.parse(time));
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.ok(response));
    }

    @Operation(summary = "축제 좋아요 상태 변경")
    @PutMapping("/{contentId}/like")
    public ResponseEntity<SuccessResponse<?>> updateFestivalLike(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable
            @Pattern(regexp = "\\d+", message = "contentId는 숫자여야 합니다.")
            @Size(max = 64, message = "contentId는 64자 이하여야 합니다.") String contentId,
            @RequestParam
            @Min(value = 2026, message = "year는 2026 이상이어야 합니다.")
            @Max(value = 9999, message = "year는 네 자리 연도여야 합니다.") int year,
            @Valid @RequestBody FestivalLikeReq request
    ) {
        festivalLikeService.updateLike(principal.getId(), contentId, year, request.liked());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.empty());
    }

    @Operation(summary = "축제 알림 설정 상태 변경")
    @PutMapping("/{contentId}/notification")
    public ResponseEntity<SuccessResponse<?>> updateFestivalNotification(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable
            @Pattern(regexp = "\\d+", message = "contentId는 숫자여야 합니다.")
            @Size(max = 64, message = "contentId는 64자 이하여야 합니다.") String contentId,
            @RequestParam
            @Min(value = 2026, message = "year는 2026 이상이어야 합니다.")
            @Max(value = 9999, message = "year는 네 자리 연도여야 합니다.") int year,
            @Valid @RequestBody FestivalNotificationReq request
    ) {
        festivalNotificationService.updateNotification(
                principal.getId(),
                contentId,
                year,
                request.enabled());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.empty());
    }
}
