package com.api.fespot.domain.festival.web.dto;

import com.api.fespot.domain.festival.entity.FestivalDetailReadModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 축제 상세 화면에 필요한 공식 기본정보
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FestivalDetailRes(
        String contentId,
        String name,
        String place,
        String address,
        LocalDate startDate,
        LocalDate endDate,
        String operatingHours,
        String imageUrl,
        String imageCopyrightType,
        String source,
        Instant fetchedAt
) {

    public static FestivalDetailRes from(FestivalDetailReadModel festival) {
        return new FestivalDetailRes(
                festival.contentId(),
                festival.name(),
                festival.place(),
                festival.address(),
                festival.startDate(),
                festival.endDate(),
                festival.operatingHours(),
                festival.imageUrl(),
                festival.imageCopyrightType(),
                festival.source(),
                festival.fetchedAt());
    }
}
