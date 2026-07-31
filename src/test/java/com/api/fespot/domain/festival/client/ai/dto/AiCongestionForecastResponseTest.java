package com.api.fespot.domain.festival.client.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiCongestionForecastResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesDailyFestivalCongestionForecastAndIgnoresUnusedFields()
            throws Exception {
        AiDailyFestivalCongestionForecastResponse response =
                objectMapper.readValue(
                        """
                                {
                                  "festival_id": "busan_sea_festival",
                                  "festival_name": "부산바다축제",
                                  "date": "2026-08-07",
                                  "recommended_arrival": {
                                    "time": "16:00",
                                    "time_range": "16:00~17:00",
                                    "main_event_time": "19:00",
                                    "expected_congestion": {
                                      "score": 34.3,
                                      "level": "보통"
                                    },
                                    "reason": "추천 이유"
                                  },
                                  "hourly_congestion": [
                                    {
                                      "time": "19:00~20:00",
                                      "festival_congestion": {
                                        "score": 59.0,
                                        "level": "보통"
                                      }
                                    }
                                  ],
                                  "data_metadata": {
                                    "weather": {
                                      "source": "festival_default"
                                    }
                                  }
                                }
                                """,
                        AiDailyFestivalCongestionForecastResponse.class);

        assertThat(response.festivalId())
                .isEqualTo("busan_sea_festival");
        assertThat(response.recommendedArrival().time())
                .isEqualTo("16:00");
        assertThat(response.recommendedArrival()
                .expectedCongestion().score())
                .isEqualTo(34.3);
        assertThat(response.hourlyCongestion()).hasSize(1);
        assertThat(response.hourlyCongestion().get(0)
                .festivalCongestion().level())
                .isEqualTo("보통");
    }

    @Test
    void deserializesFestivalAreaCongestionForecastAndIgnoresNearbyData()
            throws Exception {
        AiFestivalAreaCongestionForecastResponse response =
                objectMapper.readValue(
                        """
                                {
                                  "festival_id": "busan_sea_festival",
                                  "festival_name": "부산바다축제",
                                  "date": "2026-08-07",
                                  "time": "19:00",
                                  "time_range": "19:00~20:00",
                                  "festival_congestion": {
                                    "score": 59.0,
                                    "level": "보통"
                                  },
                                  "nearby_1km_summary": {
                                    "radius_m": 1000,
                                    "place_count": 1
                                  },
                                  "data_metadata": {}
                                }
                                """,
                        AiFestivalAreaCongestionForecastResponse.class);

        assertThat(response.festivalId())
                .isEqualTo("busan_sea_festival");
        assertThat(response.timeRange()).isEqualTo("19:00~20:00");
        assertThat(response.festivalCongestion().score())
                .isEqualTo(59.0);
        assertThat(response.festivalCongestion().level())
                .isEqualTo("보통");
    }
}
