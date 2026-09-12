package com.api.fespot.domain.report.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
