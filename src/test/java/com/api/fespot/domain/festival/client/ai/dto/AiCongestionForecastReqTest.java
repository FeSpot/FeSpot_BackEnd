package com.api.fespot.domain.festival.client.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiCongestionForecastReqTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesDailyFestivalCongestionForecastRequestForAiApi() {
        AiDailyFestivalCongestionForecastReq request =
                new AiDailyFestivalCongestionForecastReq(
                        "부산바다축제",
                        "2026-08-07",
                        false);

        JsonNode json = objectMapper.valueToTree(request);

        assertThat(json.get("festival_name").asText())
                .isEqualTo("부산바다축제");
        assertThat(json.get("date").asText()).isEqualTo("2026-08-07");
        assertThat(json.get("force_refresh").asBoolean()).isFalse();
    }

    @Test
    void serializesFestivalAreaCongestionForecastRequestForAiApi() {
        AiFestivalAreaCongestionForecastReq request =
                new AiFestivalAreaCongestionForecastReq(
                        "busan_sea_festival",
                        "2026-08-07",
                        "19:00",
                        false);

        JsonNode json = objectMapper.valueToTree(request);

        assertThat(json.get("festival_id").asText())
                .isEqualTo("busan_sea_festival");
        assertThat(json.get("date").asText()).isEqualTo("2026-08-07");
        assertThat(json.get("time").asText()).isEqualTo("19:00");
        assertThat(json.get("force_refresh").asBoolean()).isFalse();
    }
}
