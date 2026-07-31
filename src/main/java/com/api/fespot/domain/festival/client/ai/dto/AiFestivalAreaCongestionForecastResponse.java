package com.api.fespot.domain.festival.client.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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
        AiCongestionResponse festivalCongestion

        //추후 반경 1km 데이터 추가
) {
}
