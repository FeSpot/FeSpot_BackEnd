package com.api.fespot.domain.festival.service;

import com.api.fespot.domain.festival.entity.FestivalAvailability;
import com.api.fespot.domain.festival.entity.FestivalDetailReadModel;
import com.api.fespot.domain.festival.entity.FestivalKey;
import com.api.fespot.domain.festival.entity.FestivalSummaryReadModel;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalRepository;
import com.api.fespot.domain.festival.web.dto.FestivalDetailRes;
import com.api.fespot.domain.festival.web.dto.FestivalHomeRes;
import com.api.fespot.global.exception.CustomException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FestivalService {

    private final FestivalRepository festivalRepository;

    public List<FestivalHomeRes> getHomeFestivals(int year) {
        try {
            List<FestivalSummaryReadModel> festivals = festivalRepository.findByYear(year);
            return Arrays.stream(FestivalKey.values())
                    .map(festivalKey -> toResponse(festivalKey, festivals))
                    .toList();
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
}
