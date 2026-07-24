package com.api.fespot.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.api.fespot.domain.festival.entity.FestivalAvailability;
import com.api.fespot.domain.festival.entity.FestivalDetailReadModel;
import com.api.fespot.domain.festival.entity.FestivalKey;
import com.api.fespot.domain.festival.entity.FestivalSummaryReadModel;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalRepository;
import com.api.fespot.domain.festival.web.dto.FestivalDetailRes;
import com.api.fespot.domain.festival.web.dto.FestivalHomeRes;
import com.api.fespot.global.exception.CustomException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FestivalServiceTest {

    @Test
    void returnsThreeTargetsInFixedOrderAndMarksMissingFestival() {
        FestivalRepository festivalRepository = mock(FestivalRepository.class);
        given(festivalRepository.findByYear(2026)).willReturn(List.of(
                festival("300", "부산국제록페스티벌"),
                festival("200", "부산국제영화제 전야제"),
                festival("100", "제21회 부산바다축제")));
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
        FestivalRepository festivalRepository = mock(FestivalRepository.class);
        given(festivalRepository.findByYear(2026))
                .willThrow(new CustomException(FestivalErrorResponseCode.KTO_API_ERROR_502));
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

    @Test
    void returnsFestivalDetail() {
        FestivalRepository festivalRepository = mock(FestivalRepository.class);
        FestivalDetailReadModel festival = detailFestival();
        given(festivalRepository.findByContentId("100"))
                .willReturn(Optional.of(festival));
        FestivalService festivalService = new FestivalService(festivalRepository);

        FestivalDetailRes result = festivalService.getFestivalDetail("100");

        assertThat(result.contentId()).isEqualTo("100");
        assertThat(result.name()).isEqualTo("부산바다축제");
        assertThat(result.place()).isEqualTo("다대포해수욕장 일원");
        assertThat(result.address()).isEqualTo("부산광역시 사하구 몰운대1길 14");
        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(result.operatingHours()).isEqualTo("19:00~22:00");
        assertThat(result.imageUrl()).isEqualTo("https://example.com/sea-festival.jpg");
        assertThat(result.imageCopyrightType()).isEqualTo("Type1");
        assertThat(result.source()).isEqualTo("한국관광공사 OpenAPI");
        assertThat(result.fetchedAt()).isEqualTo(Instant.parse("2026-07-23T07:30:00Z"));
    }

    @Test
    void throwsNotFoundWhenFestivalDoesNotExist() {
        FestivalRepository festivalRepository = mock(FestivalRepository.class);
        given(festivalRepository.findByContentId("999"))
                .willReturn(Optional.empty());
        FestivalService festivalService = new FestivalService(festivalRepository);

        assertThatThrownBy(() -> festivalService.getFestivalDetail("999"))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));
    }

    private FestivalSummaryReadModel festival(String contentId, String name) {
        return new FestivalSummaryReadModel(
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

    private FestivalDetailReadModel detailFestival() {
        return new FestivalDetailReadModel(
                "100",
                "부산바다축제",
                "다대포해수욕장 일원",
                "부산광역시 사하구 몰운대1길 14",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 3),
                "19:00~22:00",
                "https://example.com/sea-festival.jpg",
                "Type1",
                "한국관광공사 OpenAPI",
                Instant.parse("2026-07-23T07:30:00Z"));
    }
}
