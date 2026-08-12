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
    void deserializesFestivalAreaCongestionForecastWithNearbyData()
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
                                    "place_count": 3,
                                    "level": "보통"
                                  },
                                  "nearby_1km_congestion": [
                                    {
                                      "place_name": "테스트 식당",
                                      "category": "음식",
                                      "distance_m": 120.5,
                                      "score": 67.2,
                                      "level": "혼잡",
                                      "longitude": 128.9684,
                                      "latitude": 35.0472
                                    }
                                  ],
                                  "nearby_1km_category_congestion": {
                                    "restaurants": {
                                      "category": "음식점",
                                      "place_count": 1,
                                      "level": "혼잡",
                                      "places": [
                                        {
                                          "place_name": "테스트 식당",
                                          "category": "음식",
                                          "distance_m": 120.5,
                                          "score": 67.2,
                                          "level": "혼잡"
                                        }
                                      ]
                                    },
                                    "cafes": {
                                      "category": "카페",
                                      "place_count": 1,
                                      "level": "보통",
                                      "places": []
                                    },
                                    "parking_lots": {
                                      "category": "주차장",
                                      "place_count": 1,
                                      "level": "보통",
                                      "places": []
                                    }
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
        assertThat(response.nearby1kmSummary().radiusM())
                .isEqualTo(1000);
        assertThat(response.nearby1kmCongestion()).hasSize(1);
        assertThat(response.nearby1kmCongestion().get(0).placeName())
                .isEqualTo("테스트 식당");
        assertThat(response.nearby1kmCongestion().get(0).longitude())
                .isEqualTo(128.9684);
        assertThat(response.nearby1kmCategoryCongestion()
                .restaurants().placeCount())
                .isEqualTo(1);
        assertThat(response.nearby1kmCategoryCongestion()
                .restaurants().places()).hasSize(1);
        assertThat(response.nearby1kmCategoryCongestion()
                .parkingLots().category())
                .isEqualTo("주차장");
    }
}
