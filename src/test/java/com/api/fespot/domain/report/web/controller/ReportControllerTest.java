package com.api.fespot.domain.report.web.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.api.fespot.domain.report.entity.CongestionLevel;
import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.service.ReportService;
import com.api.fespot.domain.report.web.dto.ReportCreateReq;
import com.api.fespot.domain.report.web.dto.ReportFeedRes;
import com.api.fespot.domain.report.web.dto.ReportSummaryRes;
import com.api.fespot.global.config.SecurityConfig;
import com.api.fespot.global.response.CursorPage;
import com.api.fespot.global.security.AuthPrincipal;
import com.api.fespot.global.security.handler.JwtAccessDeniedHandler;
import com.api.fespot.global.security.handler.JwtAuthenticationEntryPoint;
import com.api.fespot.global.security.jwt.JwtAuthenticationFilter;
import com.api.fespot.global.security.jwt.JwtExtractor;
import com.api.fespot.global.security.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

@WebMvcTest(ReportController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtExtractor.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class
})
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    void createsReportWithoutImage() throws Exception {
        authenticate();
        Report savedReport = mock(Report.class);
        given(savedReport.getId()).willReturn(10L);
        ReportCreateReq request = new ReportCreateReq(
                35.1532,
                129.1187,
                "메인 무대 앞이 매우 혼잡해요.",
                CongestionLevel.CROWDED);
        given(reportService.createReport(1L, "100", 2026, request, null))
                .willReturn(savedReport);

        mockMvc.perform(post("/reports")
                        .param("contentId", "100")
                        .param("year", "2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "latitude": 35.1532,
                                  "longitude": 129.1187,
                                  "content": "메인 무대 앞이 매우 혼잡해요.",
                                  "congestionLevel": "CROWDED"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_201"))
                .andExpect(jsonPath("$.data").value(10));

        verify(reportService).createReport(1L, "100", 2026, request, null);
    }

    @Test
    void rejectsReportWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/reports")
                        .param("contentId", "100")
                        .param("year", "2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GLOBAL_401"));

        verifyNoInteractions(reportService);
    }

    @Test
    void rejectsReportWithBlankContent() throws Exception {
        authenticate();

        mockMvc.perform(post("/reports")
                        .param("contentId", "100")
                        .param("year", "2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "latitude": 35.1532,
                                  "longitude": 129.1187,
                                  "content": " ",
                                  "congestionLevel": "CROWDED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_BODY"));

        verifyNoInteractions(reportService);
    }

    @Test
    void rejectsReportWithInvalidFestivalParameter() throws Exception {
        authenticate();

        mockMvc.perform(post("/reports")
                        .param("contentId", "not-a-number")
                        .param("year", "2025")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_PARAMETER"));

        verifyNoInteractions(reportService);
    }

    @Test
    void allowsGuestToReadReportFeedWithDefaultPagination() throws Exception {
        ReportFeedRes item = new ReportFeedRes(
                101L,
                "https://example.com/report.jpg",
                CongestionLevel.CROWDED,
                "메인 무대 앞이 매우 혼잡해요.",
                LocalDateTime.of(2026, 8, 22, 12, 30),
                false);
        CursorPage<ReportFeedRes> response = CursorPage.ofLastPage(List.of(item));
        given(reportService.getReportFeedList("100", 2026, null, 10))
                .willReturn(response);

        mockMvc.perform(get("/reports/feed")
                        .param("contentId", "100")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].reportId").value(101))
                .andExpect(jsonPath("$.data.items[0].imageUrl")
                        .value("https://example.com/report.jpg"))
                .andExpect(jsonPath("$.data.items[0].congestionLevel")
                        .value("CROWDED"))
                .andExpect(jsonPath("$.data.items[0].description")
                        .value("메인 무대 앞이 매우 혼잡해요."))
                .andExpect(jsonPath("$.data.items[0].createdAt")
                        .value("2026-08-22T12:30:00"))
                .andExpect(jsonPath("$.data.items[0].expired").value(false))
                .andExpect(jsonPath("$.data.pageInfo.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.data.pageInfo.hasNext").value(false));

        verify(reportService).getReportFeedList("100", 2026, null, 10);
    }

    @Test
    void readsReportFeedWithRequestedCursorAndSize() throws Exception {
        ReportFeedRes item = new ReportFeedRes(
                95L,
                null,
                CongestionLevel.NORMAL,
                "입구 줄이 길어요.",
                LocalDateTime.of(2026, 8, 22, 12, 0),
                true);
        CursorPage<ReportFeedRes> response = CursorPage.of(
                List.of(item),
                95L,
                true);
        given(reportService.getReportFeedList("100", 2026, 100L, 5))
                .willReturn(response);

        mockMvc.perform(get("/reports/feed")
                        .param("contentId", "100")
                        .param("year", "2026")
                        .param("cursor", "100")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].reportId").value(95))
                .andExpect(jsonPath("$.data.items[0].imageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].expired").value(true))
                .andExpect(jsonPath("$.data.pageInfo.nextCursor").value(95))
                .andExpect(jsonPath("$.data.pageInfo.hasNext").value(true));

        verify(reportService).getReportFeedList("100", 2026, 100L, 5);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveReportFeedCursor(long cursor) throws Exception {
        mockMvc.perform(get("/reports/feed")
                        .param("contentId", "100")
                        .param("year", "2026")
                        .param("cursor", Long.toString(cursor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_PARAMETER"));

        verifyNoInteractions(reportService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 16})
    void rejectsReportFeedSizeOutsideAllowedRange(int size) throws Exception {
        mockMvc.perform(get("/reports/feed")
                        .param("contentId", "100")
                        .param("year", "2026")
                        .param("size", Integer.toString(size)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_PARAMETER"));

        verifyNoInteractions(reportService);
    }

    @Test
    void rejectsReportFeedWithNonNumericContentId() throws Exception {
        mockMvc.perform(get("/reports/feed")
                        .param("contentId", "not-a-number")
                        .param("year", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_PARAMETER"));

        verifyNoInteractions(reportService);
    }

    @Test
    void rejectsReportFeedWithYearBefore2026() throws Exception {
        mockMvc.perform(get("/reports/feed")
                        .param("contentId", "100")
                        .param("year", "2025"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_PARAMETER"));

        verifyNoInteractions(reportService);
    }

    @Test
    void allowsGuestToReadReportSummary() throws Exception {
        ReportSummaryRes item = new ReportSummaryRes(
                11L,
                4,
                CongestionLevel.CROWDED,
                LocalDateTime.of(2026, 8, 22, 12, 30));
        given(reportService.getReportSummaryList("100", 2026))
                .willReturn(List.of(item));

        mockMvc.perform(get("/reports/summary")
                        .param("contentId", "100")
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS_200"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].reportLocationId").value(11))
                .andExpect(jsonPath("$.data[0].reportCount").value(4))
                .andExpect(jsonPath("$.data[0].representativeLevel")
                        .value("CROWDED"))
                .andExpect(jsonPath("$.data[0].latestReportedAt")
                        .value("2026-08-22T12:30:00"));

        verify(reportService).getReportSummaryList("100", 2026);
    }

    @Test
    void rejectsReportSummaryWithNonNumericContentId() throws Exception {
        mockMvc.perform(get("/reports/summary")
                        .param("contentId", "not-a-number")
                        .param("year", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_PARAMETER"));

        verifyNoInteractions(reportService);
    }

    @Test
    void rejectsReportSummaryWithYearBefore2026() throws Exception {
        mockMvc.perform(get("/reports/summary")
                        .param("contentId", "100")
                        .param("year", "2025"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL_400_PARAMETER"));

        verifyNoInteractions(reportService);
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

    private String validRequestBody() {
        return """
                {
                  "latitude": 35.1532,
                  "longitude": 129.1187,
                  "content": "메인 무대 앞이 매우 혼잡해요.",
                  "congestionLevel": "CROWDED"
                }
                """;
    }
}
