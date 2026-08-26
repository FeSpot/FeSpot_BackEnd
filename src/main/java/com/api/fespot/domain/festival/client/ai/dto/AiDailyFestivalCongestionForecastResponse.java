package com.api.fespot.domain.festival.client.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiDailyFestivalCongestionForecastResponse(
        @JsonProperty("festival_id")
        String festivalId,

        @JsonProperty("festival_name")
        String festivalName,

        String date,

        @JsonProperty("recommended_arrival")
        RecommendedArrival recommendedArrival,

        @JsonProperty("hourly_congestion")
        List<HourlyCongestion> hourlyCongestion
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecommendedArrival(
            String time,

            @JsonProperty("time_range")
            String timeRange,

            @JsonProperty("main_event_time")
            String mainEventTime,

            @JsonProperty("expected_congestion")
            AiCongestionResponse expectedCongestion,

            String reason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HourlyCongestion(
            String time,

            @JsonProperty("festival_congestion")
            AiCongestionResponse festivalCongestion
    ) {
    }
}
