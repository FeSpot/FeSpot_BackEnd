package com.api.fespot.domain.festival.client.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiDailyFestivalCongestionForecastReq(
        @JsonProperty("festival_name")
        String festivalName,

        String date,

        @JsonProperty("force_refresh")
        boolean forceRefresh
) {
}
