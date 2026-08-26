package com.api.fespot.domain.festival.web.dto;

import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastResponse;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastResponse.NearbyPlaceCongestion;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FestivalAreaCongestionForecastRes(
        String festivalId,
        String festivalName,
        String date,
        String time,
        String timeRange,
        Integer radiusM,
        Integer placeCount,
        List<Facility> facilities
) {

    public static FestivalAreaCongestionForecastRes from(
            AiFestivalAreaCongestionForecastResponse forecast
    ) {
        return new FestivalAreaCongestionForecastRes(
                forecast.festivalId(),
                forecast.festivalName(),
                forecast.date(),
                forecast.time(),
                forecast.timeRange(),
                forecast.nearby1kmSummary().radiusM(),
                forecast.nearby1kmSummary().placeCount(),
                forecast.nearby1kmCongestion().stream()
                        .map(Facility::from)
                        .toList());
    }

    public record Facility(
            String placeName,
            String category,
            Double distanceM,
            Double score,
            String level,
            Double longitude,
            Double latitude
    ) {

        private static Facility from(NearbyPlaceCongestion facility) {
            return new Facility(
                    facility.placeName(),
                    facility.category(),
                    facility.distanceM(),
                    facility.score(),
                    facility.level(),
                    facility.longitude(),
                    facility.latitude());
        }
    }
}
