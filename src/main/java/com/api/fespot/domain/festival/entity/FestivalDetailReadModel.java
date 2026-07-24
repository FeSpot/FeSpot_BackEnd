package com.api.fespot.domain.festival.entity;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 축제 상세정보를 조회하는 동안에만 사용하는 모델이며 영속화하지 않는다.
 */
public record FestivalDetailReadModel(
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
}
