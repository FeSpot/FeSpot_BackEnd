package com.api.fespot.domain.interest.web.dto;

public record InterestFestivalInfo(
        Long interestFestivalId,
        String contentId,
        String festivalName,
        String festivalDuration
) {
}
