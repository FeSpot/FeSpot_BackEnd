package com.api.fespot.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.fespot.domain.festival.entity.FestivalAvailability;
import com.api.fespot.domain.festival.entity.FestivalKey;
import com.api.fespot.domain.festival.entity.FestivalReadModel;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalRepository;
import com.api.fespot.domain.festival.web.dto.FestivalHomeRes;
import com.api.fespot.global.exception.CustomException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FestivalServiceTest {

    @Test
    void returnsThreeTargetsInFixedOrderAndMarksMissingFestival() {
        FestivalRepository festivalRepository = year -> List.of(
                festival("300", "부산국제록페스티벌"),
                festival("200", "부산국제영화제 전야제"),
                festival("100", "제21회 부산불꽃축제"));
        FestivalService festivalService = new FestivalService(festivalRepository);

        List<FestivalHomeRes> result = festivalService.getHomeFestivals(2026);

        assertThat(result)
                .extracting(FestivalHomeRes::festivalKey)
                .containsExactly(
                        FestivalKey.BUSAN_FIREWORKS_FESTIVAL,
                        FestivalKey.BUSAN_INTERNATIONAL_FILM_FESTIVAL,
                        FestivalKey.BUSAN_INTERNATIONAL_ROCK_FESTIVAL);
        assertThat(result)
                .extracting(FestivalHomeRes::availability)
                .containsExactly(
                        FestivalAvailability.AVAILABLE,
                        FestivalAvailability.NOT_FOUND,
                        FestivalAvailability.AVAILABLE);
        assertThat(result.get(0).festival().contentId()).isEqualTo("100");
        assertThat(result.get(1).festival()).isNull();
        assertThat(result.get(2).festival().contentId()).isEqualTo("300");
    }

    @Test
    void marksAllTargetsAsApiErrorWhenKtoRequestFails() {
        FestivalRepository festivalRepository = year -> {
            throw new CustomException(FestivalErrorResponseCode.KTO_API_ERROR_502);
        };
        FestivalService festivalService = new FestivalService(festivalRepository);

        List<FestivalHomeRes> result = festivalService.getHomeFestivals(2026);

        assertThat(result).hasSize(3);
        assertThat(result)
                .extracting(FestivalHomeRes::availability)
                .containsOnly(FestivalAvailability.API_ERROR);
        assertThat(result)
                .extracting(FestivalHomeRes::festival)
                .containsOnlyNulls();
    }

    private FestivalReadModel festival(String contentId, String name) {
        return new FestivalReadModel(
                contentId,
                name,
                "부산광역시 사상구 삼락동",
                LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 4),
                "https://example.com/festival.jpg",
                "Type1",
                "한국관광공사 OpenAPI",
                Instant.parse("2026-07-22T00:00:00Z"));
    }
}
