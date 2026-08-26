package com.api.fespot.domain.festival.client.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiFestivalAreaCongestionForecastReq(
        @JsonProperty("festival_id")
        String festivalId,

        String date,

        String time,

        @JsonProperty("force_refresh")
        boolean forceRefresh
) {
}
