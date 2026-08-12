package com.api.fespot.domain.festival.client.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiFestivalAreaCongestionForecastResponse(
        @JsonProperty("festival_id")
        String festivalId,

        @JsonProperty("festival_name")
        String festivalName,

        String date,
        String time,

        @JsonProperty("time_range")
        String timeRange,

        @JsonProperty("festival_congestion")
        AiCongestionResponse festivalCongestion,

        @JsonProperty("nearby_1km_summary")
        NearbySummary nearby1kmSummary,

        @JsonProperty("nearby_1km_congestion")
        List<NearbyPlaceCongestion> nearby1kmCongestion,

        @JsonProperty("nearby_1km_category_congestion")
        NearbyCategoryCongestion nearby1kmCategoryCongestion
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NearbySummary(
            @JsonProperty("radius_m")
            Integer radiusM,

            @JsonProperty("place_count")
            Integer placeCount
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NearbyPlaceCongestion(
            @JsonProperty("place_name")
            String placeName,

            String category,

            @JsonProperty("distance_m")
            Double distanceM,

            Double score,
            String level,
            Double longitude,
            Double latitude
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NearbyCategoryCongestion(
            CategoryCongestion restaurants,
            CategoryCongestion cafes,

            @JsonProperty("parking_lots")
            CategoryCongestion parkingLots
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoryCongestion(
            String category,

            @JsonProperty("place_count")
            Integer placeCount,

            @JsonProperty("average_score")
            Double averageScore,

            String level,
            List<NearbyPlaceCongestion> places
    ) {
    }
}
