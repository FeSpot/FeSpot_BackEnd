package com.api.fespot.domain.interest.web.controller;

import com.api.fespot.domain.interest.enums.InterestFestivalSuccessResponseCode;
import com.api.fespot.domain.interest.service.InterestFestivalService;
import com.api.fespot.domain.interest.web.dto.InterestFestivalInfo;
import com.api.fespot.global.response.CursorPage;
import com.api.fespot.global.response.SuccessResponse;
import com.api.fespot.global.security.AuthPrincipal;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/festival/interest")
public class InterestFestivalController {

    private final InterestFestivalService interestFestivalService;

    @PostMapping("/{contentId}")
    public ResponseEntity<?> createInterestFestival(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Pattern(regexp = "\\d+", message = "contentId는 숫자여야 합니다.") @Size(max = 64, message = "contentId는 64자 이하여야 합니다.") String contentId,
            @RequestParam(defaultValue = "2026") @Min(value = 2026, message = "year는 2026 이상이어야 합니다.") @Max(value = 9999, message = "year는 네 자리 연도여야 합니다.") int currentYear
    ) {

        Long index = interestFestivalService.saveInterestFestival(principal.getId(), currentYear, contentId);

        return ResponseEntity
                .created(URI.create("/api/festival/interest/" + index))
                .body(SuccessResponse.empty());
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<CursorPage<InterestFestivalInfo>>> readMyInterestFestival(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @Positive(message = "cursor는 양의 정수여야 합니다.") Long cursor,
            @RequestParam(defaultValue = "2026") @Min(value = 2026, message = "year는 2026 이상이어야 합니다.") @Max(value = 9999, message = "year는 네 자리 연도여야 합니다.") int currentYear,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size는 1 이상이어야 합니다.") @Max(value = 20, message = "size는 20 이하여야 합니다.") int size
    ) {
        CursorPage<InterestFestivalInfo> response = interestFestivalService.findPagedInterestFestival(principal.getId(), currentYear, cursor, size);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.of(response, InterestFestivalSuccessResponseCode.INTEREST_FESTIVAL_GET_MY_PAGED_INTEREST));
    }

    @DeleteMapping("/{interestId}")
    public ResponseEntity<SuccessResponse<Void>> deleteInterestFestival(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long interestId
    ) {
        interestFestivalService.deleteInterestFestival(principal.getId(), interestId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.empty());
    }
}
