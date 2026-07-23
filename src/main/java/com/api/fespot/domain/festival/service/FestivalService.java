package com.api.fespot.domain.festival.service;

import com.api.fespot.domain.festival.entity.FestivalAvailability;
import com.api.fespot.domain.festival.entity.FestivalKey;
import com.api.fespot.domain.festival.entity.FestivalReadModel;
import com.api.fespot.domain.festival.repository.FestivalRepository;
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
            List<FestivalReadModel> festivals = festivalRepository.findByYear(year);
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

    private FestivalHomeRes toResponse(FestivalKey festivalKey, List<FestivalReadModel> festivals) {
        return festivals.stream()
                .filter(festival -> festivalKey.matches(festival.name()))
                .findFirst()
                .map(festival -> FestivalHomeRes.available(festivalKey, festival))
                .orElseGet(() -> FestivalHomeRes.unavailable(
                        festivalKey,
                        FestivalAvailability.NOT_FOUND));
    }
}
