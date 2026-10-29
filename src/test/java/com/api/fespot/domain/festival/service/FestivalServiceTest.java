package com.api.fespot.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.fespot.domain.festival.client.ai.AiFestivalClient;
import com.api.fespot.domain.festival.client.ai.dto.AiCongestionResponse;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastReq;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastResponse;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastResponse.HourlyCongestion;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastResponse.RecommendedArrival;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastReq;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastResponse;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastResponse.NearbyPlaceCongestion;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastResponse.NearbySummary;
import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalAvailability;
import com.api.fespot.domain.festival.entity.FestivalDetailReadModel;
import com.api.fespot.domain.festival.entity.FestivalHomeReadModel;
import com.api.fespot.domain.festival.entity.FestivalKey;
import com.api.fespot.domain.festival.entity.FestivalSummaryReadModel;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.festival.repository.FestivalRepository;
import com.api.fespot.global.exception.CustomException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FestivalServiceTest {

    @Test
    void returnsThreeTargetsInFixedOrderAndMarksMissingFestival() {
        FestivalRepository festivalRepository = mock(FestivalRepository.class);
        FestivalJpaRepository festivalJpaRepository =
                mock(FestivalJpaRepository.class);
        given(festivalRepository.findByYear(2026)).willReturn(List.of(
                festival("300", "부산국제록페스티벌"),
                festival("200", "부산국제영화제 전야제"),
                festival("100", "제21회 부산바다축제")));
        FestivalService festivalService = new FestivalService(
                festivalRepository,
                festivalJpaRepository,
                mock(AiFestivalClient.class));

        List<FestivalHomeReadModel> result = festivalService.getHomeFestivals(2026);

        assertThat(result)
                .extracting(FestivalHomeReadModel::festivalKey)
                .containsExactly(
                        FestivalKey.BUSAN_SEA_FESTIVAL,
                        FestivalKey.BUSAN_INTERNATIONAL_FILM_FESTIVAL,
                        FestivalKey.BUSAN_INTERNATIONAL_ROCK_FESTIVAL);
        assertThat(result)
                .extracting(FestivalHomeReadModel::availability)
                .containsExactly(
                        FestivalAvailability.AVAILABLE,
                        FestivalAvailability.NOT_FOUND,
                        FestivalAvailability.AVAILABLE);
        assertThat(result.get(0).festival().contentId()).isEqualTo("100");
        assertThat(result.get(1).festival()).isNull();
        assertThat(result.get(2).festival().contentId()).isEqualTo("300");

        ArgumentCaptor<Iterable<Festival>> festivalsCaptor =
                ArgumentCaptor.captor();
        verify(festivalJpaRepository).saveAll(festivalsCaptor.capture());
        assertThat(festivalsCaptor.getValue())
                .extracting(Festival::getContentId)
                .containsExactlyInAnyOrder("100", "300");
        assertThat(festivalsCaptor.getValue())
                .extracting(Festival::getYear)
                .containsOnly(2026);
        assertThat(festivalsCaptor.getValue())
                .extracting(Festival::getFestivalKey)
                .containsExactly(FestivalKey.BUSAN_SEA_FESTIVAL, FestivalKey.BUSAN_INTERNATIONAL_ROCK_FESTIVAL);
    }

    @Test
    void doesNotRegisterFestivalWhenItAlreadyExists() {
        FestivalRepository festivalRepository = mock(FestivalRepository.class);
        FestivalJpaRepository festivalJpaRepository =
                mock(FestivalJpaRepository.class);
        given(festivalRepository.findByYear(2026))
                .willReturn(List.of(festival("100", "부산바다축제")));
        given(festivalJpaRepository.findByContentIdAndYear("100", 2026))
                .willReturn(Optional.of(Festival.create("100", 2026, FestivalKey.BUSAN_SEA_FESTIVAL)));
        FestivalService festivalService = new FestivalService(
                festivalRepository,
                festivalJpaRepository,
                mock(AiFestivalClient.class));

        festivalService.getHomeFestivals(2026);

        ArgumentCaptor<Iterable<Festival>> festivalsCaptor =
                ArgumentCaptor.captor();
        verify(festivalJpaRepository).saveAll(festivalsCaptor.capture());
        assertThat(festivalsCaptor.getValue()).isEmpty();
    }

    @Test
    void fillsMissingFestivalKeyWithoutCreatingAnotherFestival() {
        FestivalRepository festivalRepository = mock(FestivalRepository.class);
        FestivalJpaRepository festivalJpaRepository = mock(FestivalJpaRepository.class);
        Festival existing = Festival.create("100", 2026);
        given(festivalRepository.findByYear(2026))
                .willReturn(List.of(festival("100", "부산바다축제")));
        given(festivalJpaRepository.findByContentIdAndYear("100", 2026))
                .willReturn(Optional.of(existing));
        FestivalService festivalService = new FestivalService(
                festivalRepository, festivalJpaRepository, mock(AiFestivalClient.class));

        festivalService.getHomeFestivals(2026);

        assertThat(existing.getFestivalKey()).isEqualTo(FestivalKey.BUSAN_SEA_FESTIVAL);
        verify(festivalJpaRepository).saveAll(List.of(existing));
    }

    @Test
    void marksAllTargetsAsApiErrorWhenKtoRequestFails() {
        FestivalRepository festivalRepository = mock(FestivalRepository.class);
        FestivalJpaRepository festivalJpaRepository =
                mock(FestivalJpaRepository.class);
        given(festivalRepository.findByYear(2026))
                .willThrow(new CustomException(FestivalErrorResponseCode.KTO_API_ERROR_502));
        FestivalService festivalService = new FestivalService(
                festivalRepository,
                festivalJpaRepository,
                mock(AiFestivalClient.class));

        List<FestivalHomeReadModel> result = festivalService.getHomeFestivals(2026);

        assertThat(result).hasSize(3);
        assertThat(result)
                .extracting(FestivalHomeReadModel::availability)
                .containsOnly(FestivalAvailability.API_ERROR);
        assertThat(result)
                .extracting(FestivalHomeReadModel::festival)
                .containsOnlyNulls();
        verifyNoInteractions(festivalJpaRepository);
    }

    @Test
    void returnsFestivalDetail() {
        FestivalRepository festivalRepository = mock(FestivalRepository.class);
        FestivalJpaRepository festivalJpaRepository =
                mock(FestivalJpaRepository.class);
        AiFestivalClient aiFestivalClient = mock(AiFestivalClient.class);
        FestivalDetailReadModel festival = detailFestival();
        given(festivalRepository.findByContentId("100"))
                .willReturn(Optional.of(festival));
        FestivalService festivalService = new FestivalService(
                festivalRepository,
                festivalJpaRepository,
                aiFestivalClient);

        FestivalDetailReadModel result = festivalService.getFestivalDetail("100");

        assertThat(result.contentId()).isEqualTo("100");
        assertThat(result.name()).isEqualTo("부산바다축제");
        assertThat(result.place()).isEqualTo("다대포해수욕장 일원");
        assertThat(result.address()).isEqualTo("부산광역시 사하구 몰운대1길 14");
        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(result.endDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(result.operatingHours()).isEqualTo("19:00~22:00");
        assertThat(result.program()).isEqualTo("개막식 및 해변 공연");
        assertThat(result.imageUrl()).isEqualTo("https://example.com/sea-festival.jpg");
        assertThat(result.imageCopyrightType()).isEqualTo("Type1");
        assertThat(result.source()).isEqualTo("한국관광공사 OpenAPI");
        assertThat(result.fetchedAt()).isEqualTo(Instant.parse("2026-07-23T07:30:00Z"));
        verifyNoInteractions(aiFestivalClient);
        verifyNoInteractions(festivalJpaRepository);
    }

    @Test
    void returnsDailyFestivalCongestionForecastWithoutKtoCall() {
        FestivalRepository festivalRepository = mock(FestivalRepository.class);
        FestivalJpaRepository festivalJpaRepository =
                mock(FestivalJpaRepository.class);
        AiFestivalClient aiFestivalClient = mock(AiFestivalClient.class);
        AiDailyFestivalCongestionForecastResponse forecast = dailyForecast();
        AiDailyFestivalCongestionForecastReq request =
                new AiDailyFestivalCongestionForecastReq(
                        "부산바다축제",
                        "2026-08-07",
                        false);
        given(aiFestivalClient.fetchDailyFestivalCongestionForecast(request))
                .willReturn(forecast);
        FestivalService festivalService = new FestivalService(
                festivalRepository,
                festivalJpaRepository,
                aiFestivalClient);

        AiDailyFestivalCongestionForecastResponse result =
                festivalService.getDailyFestivalCongestionForecast(
                        FestivalKey.BUSAN_SEA_FESTIVAL,
                        LocalDate.of(2026, 8, 7));

        assertThat(result).isSameAs(forecast);
        verify(aiFestivalClient).fetchDailyFestivalCongestionForecast(request);
        verifyNoInteractions(festivalRepository, festivalJpaRepository);
    }

    @Test
    void returnsFestivalAreaCongestionForecastWithoutRepositoryCall() {
        FestivalRepository festivalRepository = mock(FestivalRepository.class);
        FestivalJpaRepository festivalJpaRepository =
                mock(FestivalJpaRepository.class);
        AiFestivalClient aiFestivalClient = mock(AiFestivalClient.class);
        AiFestivalAreaCongestionForecastResponse forecast = areaForecast();
        AiFestivalAreaCongestionForecastReq request =
                new AiFestivalAreaCongestionForecastReq(
                        "busan_sea_festival",
                        "2026-08-07",
                        "19:00",
                        false);
        given(aiFestivalClient.fetchFestivalAreaCongestionForecast(request))
                .willReturn(forecast);
        FestivalService festivalService = new FestivalService(
                festivalRepository,
                festivalJpaRepository,
                aiFestivalClient);

        AiFestivalAreaCongestionForecastResponse result =
                festivalService.getFestivalAreaCongestionForecast(
                        FestivalKey.BUSAN_SEA_FESTIVAL,
                        LocalDate.of(2026, 8, 7),
                        LocalTime.of(19, 0));

        assertThat(result).isSameAs(forecast);
        verify(aiFestivalClient).fetchFestivalAreaCongestionForecast(request);
        verifyNoInteractions(festivalRepository, festivalJpaRepository);
    }

    @Test
    void throwsNotFoundWhenFestivalDoesNotExist() {
        FestivalRepository festivalRepository = mock(FestivalRepository.class);
        FestivalJpaRepository festivalJpaRepository =
                mock(FestivalJpaRepository.class);
        AiFestivalClient aiFestivalClient = mock(AiFestivalClient.class);
        given(festivalRepository.findByContentId("999"))
                .willReturn(Optional.empty());
        FestivalService festivalService = new FestivalService(
                festivalRepository,
                festivalJpaRepository,
                aiFestivalClient);

        assertThatThrownBy(() -> festivalService.getFestivalDetail("999"))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));
        verifyNoInteractions(aiFestivalClient);
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
                "개막식 및 해변 공연",
                "https://example.com/sea-festival.jpg",
                "Type1",
                "한국관광공사 OpenAPI",
                Instant.parse("2026-07-23T07:30:00Z"));
    }

    private AiDailyFestivalCongestionForecastResponse dailyForecast() {
        return new AiDailyFestivalCongestionForecastResponse(
                "busan_sea_festival",
                "부산바다축제",
                "2026-08-07",
                new RecommendedArrival(
                        "16:00",
                        "16:00~17:00",
                        "19:00",
                        new AiCongestionResponse(34.3, "보통"),
                        "주요 행사 전에 도착하면 혼잡을 피할 수 있습니다."),
                List.of(new HourlyCongestion(
                        "19:00~20:00",
                        new AiCongestionResponse(59.0, "보통"))));
    }

    private AiFestivalAreaCongestionForecastResponse areaForecast() {
        return new AiFestivalAreaCongestionForecastResponse(
                "busan_sea_festival",
                "부산바다축제",
                "2026-08-07",
                "19:00",
                "19:00~20:00",
                new AiCongestionResponse(59.0, "보통"),
                new NearbySummary(1000, 2),
                List.of(
                        new NearbyPlaceCongestion(
                                "테스트 식당",
                                "음식",
                                120.5,
                                67.2,
                                "혼잡",
                                128.9684,
                                35.0472),
                        new NearbyPlaceCongestion(
                                "테스트 카페",
                                "카페",
                                220.0,
                                48.0,
                                "보통",
                                128.9671,
                                35.0480)),
                null);
    }
}
