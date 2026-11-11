package com.api.fespot.domain.report.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportLikeEntityTest {

    private User user;
    private Report report;

    @BeforeEach
    void setUp() {
        user = User.create("user@example.com", "사용자");
        ReportLocation location = ReportLocation.create(
                Festival.create("123456", 2026), "메인 무대", 35.1532, 129.1187);
        report = Report.builder()
                .user(user)
                .reportLocation(location)
                .content("메인 무대 앞에 사람이 많아요.")
                .originalLocation("메인 스테이지")
                .congestionLevel(CongestionLevel.CROWDED)
                .build();
    }

    @Test
    @DisplayName("사용자와 제보로 좋아요를 생성한다")
    void createsReportLike() {
        ReportLike like = ReportLike.create(user, report);

        assertThat(like.getUser()).isSameAs(user);
        assertThat(like.getReport()).isSameAs(report);
    }

    @Test
    @DisplayName("사용자가 null이면 제보 좋아요 생성에 실패한다")
    void rejectsNullUser() {
        assertThatNullPointerException()
                .isThrownBy(() -> ReportLike.create(null, report))
                .withMessageContaining("user");
    }

    @Test
    @DisplayName("제보가 null이면 제보 좋아요 생성에 실패한다")
    void rejectsNullReport() {
        assertThatNullPointerException()
                .isThrownBy(() -> ReportLike.create(user, null))
                .withMessageContaining("report");
    }
}
