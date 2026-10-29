package com.api.fespot.domain.festival.web.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.fespot.domain.festival.client.ai.dto.AiCongestionResponse;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastResponse;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastResponse.HourlyCongestion;
import com.api.fespot.domain.festival.client.ai.dto.AiDailyFestivalCongestionForecastResponse.RecommendedArrival;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastResponse;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastResponse.NearbyPlaceCongestion;
import com.api.fespot.domain.festival.client.ai.dto.AiFestivalAreaCongestionForecastResponse.NearbySummary;
import com.api.fespot.domain.festival.entity.FestivalAvailability;
import com.api.fespot.domain.festival.entity.FestivalDetailReadModel;
import com.api.fespot.domain.festival.entity.FestivalHomeReadModel;
import com.api.fespot.domain.festival.entity.FestivalKey;
import com.api.fespot.domain.festival.entity.FestivalSummaryReadModel;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.service.FestivalLikeService;
import com.api.fespot.domain.festival.service.FestivalNotificationService;
import com.api.fespot.domain.festival.service.FestivalService;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.config.SecurityConfig;
import com.api.fespot.global.security.AuthPrincipal;
import com.api.fespot.global.security.handler.JwtAccessDeniedHandler;
import com.api.fespot.global.security.handler.JwtAuthenticationEntryPoint;
import com.api.fespot.global.security.jwt.JwtAuthenticationFilter;
import com.api.fespot.global.security.jwt.JwtExtractor;
import com.api.fespot.global.security.jwt.JwtTokenProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FestivalController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtExtractor.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class FestivalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FestivalService festivalService;

    @MockitoBean
    private FestivalLikeService festivalLikeService;

    @MockitoBean
    private FestivalNotificationService festivalNotificationService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @ParameterizedTest
    @EnumSource(value = FestivalAvailability.class, names = {"NOT_FOUND", "API_ERROR"})
    void allowsGuestToReadHomeFestivals(FestivalAvailability availability) throws Exception {
        List<FestivalHomeReadModel> response = Arrays.stream(FestivalKey.values())
                .map(key -> FestivalHomeReadModel.unavailable(key, availability))
                .toList();
        given(festivalService.getHomeFestivals(2026)).willReturn(response);

        mockMvc.perform(get("/festivals/home").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].festivalKey").value("BUSAN_SEA_FESTIVAL"))
                .andExpect(jsonPath("$.data[0].displayName").value("부산바다축제"))
                .andExpect(jsonPath("$.data[0].availability").value(availability.name()))
                .andExpect(jsonPath("$.data[0].festival").doesNotExist());
    }

    @Test
    void returnsAvailableHomeFestivalDetails() throws Exception {
        FestivalSummaryReadModel festival = new FestivalSummaryReadModel(
                "100", "부산바다축제", "다대포해수욕장",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3),
                "https://example.com/festival.jpg", "Type1", "한국관광공사 OpenAPI",
                Instant.parse("2026-07-23T07:30:00Z"));
        given(festivalService.getHomeFestivals(2026)).willReturn(List.of(
                FestivalHomeReadModel.available(FestivalKey.BUSAN_SEA_FESTIVAL, festival),
                FestivalHomeReadModel.unavailable(FestivalKey.BUSAN_INTERNATIONAL_FILM_FESTIVAL,
                        FestivalAvailability.NOT_FOUND),
                FestivalHomeReadModel.unavailable(FestivalKey.BUSAN_INTERNATIONAL_ROCK_FESTIVAL,
                        FestivalAvailability.NOT_FOUND)));

        mockMvc.perform(get("/festivals/home").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].festivalKey").value("BUSAN_SEA_FESTIVAL"))
                .andExpect(jsonPath("$.data[0].displayName").value("부산바다축제"))
                .andExpect(jsonPath("$.data[0].availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.data[0].festival.contentId").value("100"))
                .andExpect(jsonPath("$.data[0].festival.name").value("부산바다축제"))
                .andExpect(jsonPath("$.data[0].festival.address").value("다대포해수욕장"))
                .andExpect(jsonPath("$.data[0].festival.startDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data[0].festival.endDate").value("2026-08-03"))
                .andExpect(jsonPath("$.data[0].festival.imageUrl").value("https://example.com/festival.jpg"))
                .andExpect(jsonPath("$.data[0].festival.imageCopyrightType").value("Type1"))
                .andExpect(jsonPath("$.data[0].festival.source").value("한국관광공사 OpenAPI"))
                .andExpect(jsonPath("$.data[0].festival.fetchedAt").value("2026-07-23T07:30:00Z"))
                .andExpect(jsonPath("$.data[1].festivalKey").value("BUSAN_INTERNATIONAL_FILM_FESTIVAL"))
                .andExpect(jsonPath("$.data[2].festivalKey").value("BUSAN_INTERNATIONAL_ROCK_FESTIVAL"));
    }

    @Test
    void allowsGuestToReadFestivalDetailWithoutUnusedFields() throws Exception {
        FestivalDetailReadModel response = new FestivalDetailReadModel(
                "100",
                "부산바다축제",
                "다대포해수욕장 일원",
                "부산광역시 사하구 몰운대1길 14",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 3),
                "19:00~22:00",
                "개막식 및 해변 공연",
                "https://example.com/sea-festival.jpg",
                "Type1",
                "한국관광공사 OpenAPI",
                Instant.parse("2026-07-23T07:30:00Z"));
        given(festivalService.getFestivalDetail("100")).willReturn(response);

        mockMvc.perform(get("/festivals/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.contentId").value("100"))
                .andExpect(jsonPath("$.data.name").value("부산바다축제"))
                .andExpect(jsonPath("$.data.place").value("다대포해수욕장 일원"))
                .andExpect(jsonPath("$.data.address").value("부산광역시 사하구 몰운대1길 14"))
                .andExpect(jsonPath("$.data.startDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data.endDate").value("2026-08-03"))
                .andExpect(jsonPath("$.data.operatingHours").value("19:00~22:00"))
                .andExpect(jsonPath("$.data.program").value("개막식 및 해변 공연"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://example.com/sea-festival.jpg"))
                .andExpect(jsonPath("$.data.imageCopyrightType").value("Type1"))
                .andExpect(jsonPath("$.data.source").value("한국관광공사 OpenAPI"))
                .andExpect(jsonPath("$.data.fetchedAt").value("2026-07-23T07:30:00Z"))
                .andExpect(jsonPath("$.data.description").doesNotExist())
                .andExpect(jsonPath("$.data.contact").doesNotExist())
                .andExpect(jsonPath("$.data.longitude").doesNotExist())
                .andExpect(jsonPath("$.data.latitude").doesNotExist());

        verify(festivalService).getFestivalDetail("100");
    }

    @Test
    void allowsGuestToReadDailyFestivalCongestionForecast() throws Exception {
        given(festivalService.getDailyFestivalCongestionForecast(
                FestivalKey.BUSAN_SEA_FESTIVAL,
                LocalDate.of(2026, 8, 7)))
                .willReturn(dailyForecast());

        mockMvc.perform(get(
                        "/festivals/BUSAN_SEA_FESTIVAL/daily-forecast")
                        .param("date", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.festival_id")
                        .value("busan_sea_festival"))
                .andExpect(jsonPath("$.data.recommended_arrival.time")
                        .value("16:00"))
                .andExpect(jsonPath(
                        "$.data.hourly_congestion[0].festival_congestion.score")
                        .value(59.0));

        verify(festivalService).getDailyFestivalCongestionForecast(
                FestivalKey.BUSAN_SEA_FESTIVAL,
                LocalDate.of(2026, 8, 7));
    }

    @Test
    void returnsAreaForecastAsFacilities() throws Exception {
        given(festivalService.getFestivalAreaCongestionForecast(
                FestivalKey.BUSAN_SEA_FESTIVAL, LocalDate.of(2026, 8, 7), LocalTime.of(19, 0)))
                .willReturn(areaForecast());

        mockMvc.perform(get("/festivals/BUSAN_SEA_FESTIVAL/area-forecast")
                        .param("date", "2026-08-07").param("time", "19:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.festivalId").value("busan_sea_festival"))
                .andExpect(jsonPath("$.data.festivalName").value("부산바다축제"))
                .andExpect(jsonPath("$.data.date").value("2026-08-07"))
                .andExpect(jsonPath("$.data.time").value("19:00"))
                .andExpect(jsonPath("$.data.timeRange").value("19:00~20:00"))
                .andExpect(jsonPath("$.data.radiusM").value(1000))
                .andExpect(jsonPath("$.data.placeCount").value(1))
                .andExpect(jsonPath("$.data.facilities.length()").value(1))
                .andExpect(jsonPath("$.data.facilities[0].placeName").value("테스트 식당"))
                .andExpect(jsonPath("$.data.facilities[0].category").value("음식"))
                .andExpect(jsonPath("$.data.facilities[0].distanceM").value(120.5))
                .andExpect(jsonPath("$.data.facilities[0].score").value(67.2))
                .andExpect(jsonPath("$.data.facilities[0].level").value("혼잡"))
                .andExpect(jsonPath("$.data.facilities[0].longitude").value(128.9684))
                .andExpect(jsonPath("$.data.facilities[0].latitude").value(35.0472))
                .andExpect(jsonPath("$.data.nearby_1km_summary").doesNotExist())
                .andExpect(jsonPath("$.data.nearby_1km_congestion").doesNotExist());

        verify(festivalService).getFestivalAreaCongestionForecast(
                FestivalKey.BUSAN_SEA_FESTIVAL, LocalDate.of(2026, 8, 7), LocalTime.of(19, 0));
    }

    @Test
    void rejectsDailyForecastWithoutDate() throws Exception {
        mockMvc.perform(get(
                        "/festivals/BUSAN_SEA_FESTIVAL/daily-forecast"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(festivalService);
    }

    @Test
    void returnsBadRequestForForecastOutsidePredictionRange() throws Exception {
        given(festivalService.getDailyFestivalCongestionForecast(
                FestivalKey.BUSAN_SEA_FESTIVAL, LocalDate.of(2026, 8, 7)))
                .willThrow(new CustomException(
                        FestivalErrorResponseCode.AI_PREDICTION_RANGE_400));

        mockMvc.perform(get("/festivals/BUSAN_SEA_FESTIVAL/daily-forecast")
                        .param("date", "2026-08-07"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("FESTIVAL_AI_PREDICTION_RANGE_400"));
    }

    @Test
    void returnsBadGatewayForInvalidAiForecastResponse() throws Exception {
        given(festivalService.getDailyFestivalCongestionForecast(
                FestivalKey.BUSAN_SEA_FESTIVAL, LocalDate.of(2026, 8, 7)))
                .willThrow(new CustomException(FestivalErrorResponseCode.AI_API_ERROR_502));

        mockMvc.perform(get("/festivals/BUSAN_SEA_FESTIVAL/daily-forecast")
                        .param("date", "2026-08-07"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("FESTIVAL_AI_API_ERROR_502"));
    }

    @Test
    void rejectsDailyForecastWithInvalidDate() throws Exception {
        mockMvc.perform(get(
                        "/festivals/BUSAN_SEA_FESTIVAL/daily-forecast")
                        .param("date", "2026-08-32"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(festivalService);
    }

    @Test
    void rejectsUnsupportedFestivalKeyForDailyForecast() throws Exception {
        mockMvc.perform(get("/festivals/UNKNOWN/daily-forecast")
                        .param("date", "2026-08-07"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(festivalService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"07:00", "08:30", "24:00"})
    void rejectsAreaForecastWithInvalidTime(String time) throws Exception {
        authenticate();

        mockMvc.perform(get(
                        "/festivals/BUSAN_SEA_FESTIVAL/area-forecast")
                        .param("date", "2026-08-07")
                        .param("time", time)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_PARAMETER"))
                .andExpect(jsonPath("$.data[0].field").value("time"))
                .andExpect(jsonPath("$.data[0].rejectedValue").value(time))
                .andExpect(jsonPath("$.data[0].message")
                        .value("time은 08:00~23:00 범위의 정각이어야 합니다."));

        verifyNoInteractions(festivalService);
    }

    @Test
    void rejectsNonNumericFestivalContentId() throws Exception {
        mockMvc.perform(get("/festivals/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_PARAMETER"));
    }

    @Test
    void keepsOtherApiPathsProtected() throws Exception {
        mockMvc.perform(get("/not-public"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));
    }

    @Test
    void rejectsYearBefore2026() throws Exception {
        mockMvc.perform(get("/festivals/home").param("year", "2025"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_PARAMETER"));
    }

    @Test
    void updatesFestivalLikeForAuthenticatedUser() throws Exception {
        authenticate();

        mockMvc.perform(put("/festivals/100/like")
                        .param("year", "2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "liked": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(festivalLikeService).updateLike(1L, "100", 2026, true);
    }

    @Test
    void rejectsFestivalLikeWithoutAuthentication() throws Exception {
        mockMvc.perform(put("/festivals/100/like")
                        .param("year", "2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "liked": true
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(festivalLikeService);
    }

    @Test
    void rejectsFestivalLikeWhenLikedIsMissing() throws Exception {
        authenticate();

        mockMvc.perform(put("/festivals/100/like")
                        .param("year", "2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_BODY"));

        verifyNoInteractions(festivalLikeService);
    }

    @Test
    void updatesFestivalNotificationForAuthenticatedUser() throws Exception {
        authenticate();

        mockMvc.perform(put("/festivals/100/notification")
                        .param("year", "2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(festivalNotificationService)
                .updateNotification(1L, "100", 2026, true);
    }

    @Test
    void rejectsFestivalNotificationWithoutAuthentication() throws Exception {
        mockMvc.perform(put("/festivals/100/notification")
                        .param("year", "2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(festivalNotificationService);
    }

    @Test
    void rejectsFestivalNotificationWhenEnabledIsMissing() throws Exception {
        authenticate();

        mockMvc.perform(put("/festivals/100/notification")
                        .param("year", "2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_BODY"));

        verifyNoInteractions(festivalNotificationService);
    }

    private void authenticate() {
        AuthPrincipal principal =
                AuthPrincipal.of(1L, "user@example.com", List.of("USER"));
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities());
        given(jwtTokenProvider.validateToken("access-token")).willReturn(true);
        given(jwtTokenProvider.getAuthentication("access-token"))
                .willReturn(authentication);
    }

    private AiFestivalAreaCongestionForecastResponse areaForecast() {
        return new AiFestivalAreaCongestionForecastResponse(
                "busan_sea_festival", "부산바다축제", "2026-08-07", "19:00", "19:00~20:00",
                new AiCongestionResponse(59.0, "보통"),
                new NearbySummary(1000, 1),
                List.of(new NearbyPlaceCongestion(
                        "테스트 식당", "음식", 120.5, 67.2, "혼잡", 128.9684, 35.0472)),
                null);
    }

    private AiDailyFestivalCongestionForecastResponse dailyForecast() {
        return new AiDailyFestivalCongestionForecastResponse(
                "busan_sea_festival",
                "부산바다축제",
                "2026-08-07",
                new RecommendedArrival(
                        "16:00",
                        "16:00~17:00",
                        "19:00",
                        new AiCongestionResponse(34.3, "보통"),
                        "주요 행사 전에 도착하면 혼잡을 피할 수 있습니다."),
                List.of(new HourlyCongestion(
                        "19:00~20:00",
                        new AiCongestionResponse(59.0, "보통"))));
    }
}
