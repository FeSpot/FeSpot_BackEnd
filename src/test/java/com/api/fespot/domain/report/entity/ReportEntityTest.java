package com.api.fespot.domain.report.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.report.exception.ReportErrorResponseCode;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.global.exception.CustomException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReportEntityTest {

    @Test
    @DisplayName("제보 위치와 제보를 생성한다")
    void createsReportLocationAndReport() {
        Festival festival = Festival.create("123456", 2026);
        User user = User.create("user@example.com", "사용자");
        ReportLocation location = ReportLocation.create(
                festival,
                "메인 무대",
                35.1532,
                129.1187);

        Report report = createReport(
                user,
                location,
                "메인 무대 앞에 사람이 많아요.",
                CongestionLevel.CROWDED,
                null
        );

        assertThat(location.getFestival()).isSameAs(festival);
        assertThat(location.getSignatureName()).isEqualTo("메인 무대");
        assertThat(location.getAnchorLatitude()).isEqualTo(35.1532);
        assertThat(location.getAnchorLongitude()).isEqualTo(129.1187);
        assertThat(report.getUser()).isSameAs(user);
        assertThat(report.getReportLocation()).isSameAs(location);
        assertThat(report.getContent()).isEqualTo("메인 무대 앞에 사람이 많아요.");
        assertThat(report.getOriginalLocation()).isEqualTo("  메인 스테이지  ");
        assertThat(report.getCongestionLevel()).isEqualTo(CongestionLevel.CROWDED);
        assertThat(report.getImageUrl()).isNull();
    }

    @ParameterizedTest(name = "{0}이 null이면 제보 생성에 실패한다")
    @MethodSource("nullRequiredFields")
    void rejectsNullRequiredField(
            String fieldName,
            User user,
            ReportLocation location,
            String content,
            String originalLocation,
            CongestionLevel congestionLevel
    ) {
        assertThatExceptionOfType(CustomException.class)
                .isThrownBy(() -> Report.builder()
                        .user(user)
                        .reportLocation(location)
                        .content(content)
                        .originalLocation(originalLocation)
                        .congestionLevel(congestionLevel)
                        .build())
                .satisfies(exception -> assertThat(exception.getBaseResponseCode())
                        .isEqualTo(ReportErrorResponseCode.REPORT_BAD_ARGUMENT_500));
    }

    private static Stream<Arguments> nullRequiredFields() {
        User user = User.create("user@example.com", "사용자");
        ReportLocation location = ReportLocation.create(
                Festival.create("123456", 2026), "메인 무대", 35.1532, 129.1187);
        String content = "메인 무대 앞에 사람이 많아요.";
        String originalLocation = "메인 스테이지";
        CongestionLevel congestionLevel = CongestionLevel.CROWDED;

        return Stream.of(
                Arguments.of("user", null, location, content, originalLocation, congestionLevel),
                Arguments.of("reportLocation", user, null, content, originalLocation, congestionLevel),
                Arguments.of("content", user, location, null, originalLocation, congestionLevel),
                Arguments.of("originalLocation", user, location, content, null, congestionLevel),
                Arguments.of("congestionLevel", user, location, content, originalLocation, null));
    }

    private Report createReport(
            User user,
            ReportLocation location,
            String content,
            CongestionLevel congestionLevel,
            String imageUrl
    ) {
        return Report.builder()
                .user(user)
                .reportLocation(location)
                .content(content)
                .originalLocation("  메인 스테이지  ")
                .congestionLevel(congestionLevel)
                .imageUrl(imageUrl)
                .build();
    }
}
