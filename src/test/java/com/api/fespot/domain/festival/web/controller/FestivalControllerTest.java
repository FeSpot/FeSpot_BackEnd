package com.api.fespot.domain.festival.web.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.fespot.domain.festival.entity.FestivalAvailability;
import com.api.fespot.domain.festival.entity.FestivalKey;
import com.api.fespot.domain.festival.service.FestivalService;
import com.api.fespot.domain.festival.web.dto.FestivalDetailRes;
import com.api.fespot.domain.festival.web.dto.FestivalHomeRes;
import com.api.fespot.global.config.SecurityConfig;
import com.api.fespot.global.security.handler.JwtAccessDeniedHandler;
import com.api.fespot.global.security.handler.JwtAuthenticationEntryPoint;
import com.api.fespot.global.security.jwt.JwtAuthenticationFilter;
import com.api.fespot.global.security.jwt.JwtExtractor;
import com.api.fespot.global.security.jwt.JwtTokenProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
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
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void allowsGuestToReadHomeFestivals() throws Exception {
        List<FestivalHomeRes> response = Arrays.stream(FestivalKey.values())
                .map(key -> FestivalHomeRes.unavailable(key, FestivalAvailability.NOT_FOUND))
                .toList();
        given(festivalService.getHomeFestivals(2026)).willReturn(response);

        mockMvc.perform(get("/api/festivals/home").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].festivalKey").value("BUSAN_FIREWORKS_FESTIVAL"))
                .andExpect(jsonPath("$.data[0].availability").value("NOT_FOUND"));
    }

    @Test
    void allowsGuestToReadFestivalDetailWithoutUnusedFields() throws Exception {
        FestivalDetailRes response = new FestivalDetailRes(
                "100",
                "부산바다축제",
                "다대포해수욕장 일원",
                "부산광역시 사하구 몰운대1길 14",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 3),
                "19:00~22:00",
                "https://example.com/sea-festival.jpg",
                "Type1",
                "한국관광공사 OpenAPI",
                Instant.parse("2026-07-23T07:30:00Z"));
        given(festivalService.getFestivalDetail("100")).willReturn(response);

        mockMvc.perform(get("/api/festivals/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.contentId").value("100"))
                .andExpect(jsonPath("$.data.name").value("부산바다축제"))
                .andExpect(jsonPath("$.data.place").value("다대포해수욕장 일원"))
                .andExpect(jsonPath("$.data.address").value("부산광역시 사하구 몰운대1길 14"))
                .andExpect(jsonPath("$.data.startDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data.endDate").value("2026-08-03"))
                .andExpect(jsonPath("$.data.operatingHours").value("19:00~22:00"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://example.com/sea-festival.jpg"))
                .andExpect(jsonPath("$.data.imageCopyrightType").value("Type1"))
                .andExpect(jsonPath("$.data.source").value("한국관광공사 OpenAPI"))
                .andExpect(jsonPath("$.data.fetchedAt").value("2026-07-23T07:30:00Z"))
                .andExpect(jsonPath("$.data.description").doesNotExist())
                .andExpect(jsonPath("$.data.contact").doesNotExist())
                .andExpect(jsonPath("$.data.longitude").doesNotExist())
                .andExpect(jsonPath("$.data.latitude").doesNotExist());
    }

    @Test
    void rejectsNonNumericFestivalContentId() throws Exception {
        mockMvc.perform(get("/api/festivals/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_PARAMETER"));
    }

    @Test
    void keepsOtherApiPathsProtected() throws Exception {
        mockMvc.perform(get("/api/not-public"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));
    }

    @Test
    void rejectsYearBefore2026() throws Exception {
        mockMvc.perform(get("/api/festivals/home").param("year", "2025"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_PARAMETER"));
    }
}
