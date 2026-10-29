package com.api.fespot.domain.report.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.report.exception.ReportErrorResponseCode;
import com.api.fespot.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReportLocationEntityTest {

    @Test
    @DisplayName("축제가 null이면 제보 위치 생성에 실패한다")
    void rejectsNullFestival() {
        assertThatExceptionOfType(CustomException.class)
                .isThrownBy(() -> ReportLocation.create(null, "메인 무대", 35.1532, 129.1187))
                .satisfies(exception -> assertThat(exception.getBaseResponseCode())
                        .isEqualTo(ReportErrorResponseCode.REPORT_BAD_ARGUMENT_500));
    }

    @Test
    @DisplayName("대표 장소명이 null이어도 제보 위치를 생성하고 마지막 제보 시각을 채운다")
    void createsLocationWithoutSignatureName() {
        Festival festival = Festival.create("123456", 2026);

        ReportLocation location = ReportLocation.create(festival, null, 35.1532, 129.1187);

        assertThat(location.getFestival()).isSameAs(festival);
        assertThat(location.getSignatureName()).isNull();
        assertThat(location.getLastReportedAt()).isNotNull();
    }
}
