package com.api.fespot.domain.festival.service;

import com.api.fespot.domain.festival.client.ai.AiFestivalClient;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastReq;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastResponse;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastReq;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastResponse;
import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalAvailability;
import com.api.fespot.domain.festival.entity.FestivalDetailReadModel;
import com.api.fespot.domain.festival.entity.FestivalKey;
import com.api.fespot.domain.festival.entity.FestivalSummaryReadModel;
import com.api.fespot.domain.festival.exception.DuplicateFestivalException;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.festival.repository.FestivalRepository;
import com.api.fespot.domain.festival.web.dto.FestivalAreaCongestionForecastRes;
import com.api.fespot.domain.festival.web.dto.FestivalDetailRes;
import com.api.fespot.domain.festival.web.dto.FestivalHomeRes;
import com.api.fespot.global.aop.CreateUniqueColumn;
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

    @CreateUniqueColumn(
            value = DuplicateFestivalException.class,
            constraint = "uk_festival_contents_id_year"
    )
    public List<FestivalHomeRes> getHomeFestivals(int year) {
        try {
            List<FestivalSummaryReadModel> festivals = festivalRepository.findByYear(year);
            List<FestivalHomeRes> responses = Arrays.stream(FestivalKey.values())
                    .map(festivalKey -> toResponse(festivalKey, festivals))
                    .toList();

            registerFestivals(responses, year);

            return responses;
        } catch (CustomException e) {
            return Arrays.stream(FestivalKey.values())
                    .map(festivalKey -> FestivalHomeRes.unavailable(
                            festivalKey,
                            FestivalAvailability.API_ERROR))
                    .toList();
        }
    }

    public FestivalDetailRes getFestivalDetail(String contentId) {
        FestivalDetailReadModel festival = festivalRepository.findByContentId(contentId)
                .orElseThrow(() -> new CustomException(
                        FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));

        return FestivalDetailRes.from(festival);
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
    public FestivalAreaCongestionForecastRes getFestivalAreaCongestionForecast(
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
        AiFestivalAreaCongestionForecastResponse forecast =
                aiFestivalClient.fetchFestivalAreaCongestionForecast(request);
        return FestivalAreaCongestionForecastRes.from(forecast);
    }

    private FestivalHomeRes toResponse(
            FestivalKey festivalKey,
            List<FestivalSummaryReadModel> festivals
    ) {
        return festivals.stream()
                .filter(festival -> festivalKey.matches(festival.name()))
                .findFirst()
                .map(festival -> FestivalHomeRes.available(festivalKey, festival))
                .orElseGet(() -> FestivalHomeRes.unavailable(
                        festivalKey,
                        FestivalAvailability.NOT_FOUND));
    }

    private void registerFestivals(
            List<FestivalHomeRes> responses,
            int year
    ) {
        List<Festival> festivals = new ArrayList<>();
        for (FestivalHomeRes response : responses) {
            if (response.availability() != FestivalAvailability.AVAILABLE) {
                continue;
            }

            String contentId = response.festival().contentId();
            Festival festival = festivalJpaRepository.findByContentIdAndYear(contentId, year)
                    .orElse(null);
            if (festival == null) {
                festivals.add(Festival.create(contentId, year, response.festivalKey()));
            } else if (festival.getFestivalKey() == null) {
                festival.assignFestivalKeyIfAbsent(response.festivalKey());
                festivals.add(festival);
            }
        }
        festivalJpaRepository.saveAll(festivals);
    }
}
