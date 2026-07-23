package com.api.fespot.domain.festival.web.controller;

import com.api.fespot.domain.festival.service.FestivalService;
import com.api.fespot.domain.festival.web.dto.FestivalHomeRes;
import com.api.fespot.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Festival", description = "축제 조회 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/festivals")
public class FestivalController {

    private final FestivalService festivalService;

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
}
