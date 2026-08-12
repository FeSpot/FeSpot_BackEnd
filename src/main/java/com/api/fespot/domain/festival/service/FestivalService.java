package com.api.fespot.domain.festival.service;

import com.api.fespot.domain.festival.client.ai.AiFestivalClient;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastReq;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastResponse;
import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalAvailability;
import com.api.fespot.domain.festival.entity.FestivalDetailReadModel;
import com.api.fespot.domain.festival.entity.FestivalKey;
import com.api.fespot.domain.festival.entity.FestivalSummaryReadModel;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.festival.repository.FestivalRepository;
import com.api.fespot.domain.festival.web.dto.FestivalDetailRes;
import com.api.fespot.domain.festival.web.dto.FestivalHomeRes;
import com.api.fespot.global.exception.CustomException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FestivalService {

    private final FestivalRepository festivalRepository;
    private final FestivalJpaRepository festivalJpaRepository;
    private final AiFestivalClient aiFestivalClient;

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

    public AiDailyFestivalCongestionForecastResponse getDailyFestivalCongestionForecast(
            FestivalKey festivalKey,
            LocalDate date
    ) {
        AiDailyFestivalCongestionForecastReq request =
                new AiDailyFestivalCongestionForecastReq(
                        festivalKey.getDisplayName(),
                        date.toString(),
                        false);
        return aiFestivalClient.fetchDailyFestivalCongestionForecast(request);
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
        responses.stream()
                .filter(response ->
                        response.availability() == FestivalAvailability.AVAILABLE)
                .map(response -> response.festival().contentId())
                .filter(contentId ->
                        !festivalJpaRepository.existsByContentIdAndYear(
                                contentId,
                                year))
                .map(contentId -> Festival.create(contentId, year))
                .forEach(festivalJpaRepository::save);
    }
}
