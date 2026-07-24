package com.api.fespot.domain.festival.entity;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 축제 목록을 조회하는 동안에만 사용하는 모델이며 영속화하지 않는다.
 */
public record FestivalSummaryReadModel(
        String contentId,
        String name,
        String address,
        LocalDate startDate,
        LocalDate endDate,
        String imageUrl,
        String imageCopyrightType,
        String source,
        Instant fetchedAt
) {
}
