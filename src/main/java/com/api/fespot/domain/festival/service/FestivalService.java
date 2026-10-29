package com.api.fespot.domain.festival.service;

import com.api.fespot.domain.festival.client.ai.AiFestivalClient;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastReq;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastResponse;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastReq;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastResponse;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FestivalService {

    private final FestivalRepository festivalRepository;
    private final FestivalJpaRepository festivalJpaRepository;
    private final AiFestivalClient aiFestivalClient;

    public List<FestivalHomeReadModel> getHomeFestivals(int year) {
        try {
            List<FestivalSummaryReadModel> festivals = festivalRepository.findByYear(year);
            List<FestivalHomeReadModel> results = Arrays.stream(FestivalKey.values())
                    .map(festivalKey -> toHomeReadModel(festivalKey, festivals))
                    .toList();

            registerFestivals(results, year);

            return results;
        } catch (CustomException e) {
            return Arrays.stream(FestivalKey.values())
                    .map(festivalKey -> FestivalHomeReadModel.unavailable(
                            festivalKey,
                            FestivalAvailability.API_ERROR))
                    .toList();
        }
    }

    public FestivalDetailReadModel getFestivalDetail(String contentId) {
        return festivalRepository.findByContentId(contentId)
                .orElseThrow(() -> new CustomException(
                        FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));
    }

    @Cacheable(cacheNames = "dailyFestivalForecast", sync = true)
    public AiDailyFestivalCongestionForecastResponse getDailyFestivalCongestionForecast(
            FestivalKey festivalKey,
            LocalDate date
    ) {
        log.debug("Cache Miss, 일별 축제 혼잡도 호출 festivalKey={}, date={}", festivalKey, date);
        AiDailyFestivalCongestionForecastReq request =
                new AiDailyFestivalCongestionForecastReq(
                        festivalKey.getDisplayName(),
                        date.toString(),
                        false);
        return aiFestivalClient.fetchDailyFestivalCongestionForecast(request);
    }

    @Cacheable(cacheNames = "festivalAreaForecast", sync = true)
    public AiFestivalAreaCongestionForecastResponse getFestivalAreaCongestionForecast(
            FestivalKey festivalKey,
            LocalDate date,
            LocalTime time
    ) {
        log.debug("Cache Miss, 주변 시설&혼잡도 호출 festivalKey={}, date={}, time={}", festivalKey, date, time);
        AiFestivalAreaCongestionForecastReq request =
                new AiFestivalAreaCongestionForecastReq(
                        festivalKey.getAiFestivalId(),
                        date.toString(),
                        time.format(DateTimeFormatter.ofPattern("HH:mm")),
                        false);
        return aiFestivalClient.fetchFestivalAreaCongestionForecast(request);
    }

    private FestivalHomeReadModel toHomeReadModel(
            FestivalKey festivalKey,
            List<FestivalSummaryReadModel> festivals
    ) {
        return festivals.stream()
                .filter(festival -> festivalKey.matches(festival.name()))
                .findFirst()
                .map(festival -> FestivalHomeReadModel.available(festivalKey, festival))
                .orElseGet(() -> FestivalHomeReadModel.unavailable(
                        festivalKey,
                        FestivalAvailability.NOT_FOUND));
    }

    private void registerFestivals(
            List<FestivalHomeReadModel> results,
            int year
    ) {
        List<Festival> festivals = new ArrayList<>();
        for (FestivalHomeReadModel result : results) {
            if (result.availability() != FestivalAvailability.AVAILABLE) {
                continue;
            }

            String contentId = result.festival().contentId();
            Festival festival = festivalJpaRepository.findByContentIdAndYear(contentId, year)
                    .orElse(null);
            if (festival == null) {
                festivals.add(Festival.create(contentId, year, result.festivalKey()));
            } else if (festival.getFestivalKey() == null) {
                festival.assignFestivalKeyIfAbsent(result.festivalKey());
                festivals.add(festival);
            }
        }
        festivalJpaRepository.saveAll(festivals);
    }
}
