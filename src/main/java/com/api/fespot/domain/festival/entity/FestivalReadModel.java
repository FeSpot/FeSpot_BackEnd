package com.api.fespot.domain.festival.entity;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 요청 처리 중에만 사용하는 OpenAPI 조회 모델이며 영속화 대상이 아니다
 */
public record FestivalReadModel(
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
