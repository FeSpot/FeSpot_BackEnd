package com.api.fespot.domain.report.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.fespot.domain.report.entity.CongestionLevel;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class ReportCreateReqTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("이미지 URL이 없는 유효한 제보 등록 요청은 검증을 통과한다")
    void acceptsValidRequestWithoutImageUrl() {
        ReportCreateReq request = request(35.1532, 129.1187, "메인 무대 앞에 사람이 많아요.", CongestionLevel.CROWDED);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("올바른 HTTPS 이미지 URL은 검증을 통과한다")
    void acceptsValidHttpsImageUrl() {
        ReportCreateReq request = request(
                35.1532,
                129.1187,
                "메인 무대 앞에 사람이 많아요.",
                CongestionLevel.CROWDED,
                "https://fespot-bucket.s3.ap-northeast-2.amazonaws.com/reports/report.jpg");

        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/report.jpg", "invalid-url"})
    @DisplayName("HTTP 또는 잘못된 이미지 URL은 검증에 실패한다")
    void rejectsInvalidImageUrl(String imageUrl) {
        ReportCreateReq request = request(
                35.1532,
                129.1187,
                "메인 무대 앞에 사람이 많아요.",
                CongestionLevel.CROWDED,
                imageUrl);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    @DisplayName("위도가 없거나 범위를 벗어나면 검증에 실패한다")
    void rejectsInvalidLatitude() {
        assertThat(validator.validate(request(null, 129.1187, "혼잡한 상황입니다.", CongestionLevel.CROWDED))).isNotEmpty();
        assertThat(validator.validate(request(-90.1, 129.1187, "혼잡한 상황입니다.", CongestionLevel.CROWDED))).isNotEmpty();
        assertThat(validator.validate(request(90.1, 129.1187, "혼잡한 상황입니다.", CongestionLevel.CROWDED))).isNotEmpty();
    }

    @Test
    @DisplayName("경도가 없거나 범위를 벗어나면 검증에 실패한다")
    void rejectsInvalidLongitude() {
        assertThat(validator.validate(request(35.1532, null, "혼잡한 상황입니다.", CongestionLevel.CROWDED))).isNotEmpty();
        assertThat(validator.validate(request(35.1532, -180.1, "혼잡한 상황입니다.", CongestionLevel.CROWDED))).isNotEmpty();
        assertThat(validator.validate(request(35.1532, 180.1, "혼잡한 상황입니다.", CongestionLevel.CROWDED))).isNotEmpty();
    }

    @Test
    @DisplayName("제보 내용이 공백이거나 길이 범위를 벗어나면 검증에 실패한다")
    void rejectsInvalidContent() {
        assertThat(validator.validate(request(35.1532, 129.1187, " ", CongestionLevel.CROWDED))).isNotEmpty();
        assertThat(validator.validate(request(35.1532, 129.1187, "짧음", CongestionLevel.CROWDED))).isNotEmpty();
        assertThat(validator.validate(request(35.1532, 129.1187, "가".repeat(501), CongestionLevel.CROWDED))).isNotEmpty();
    }

    @Test
    @DisplayName("혼잡도가 없으면 검증에 실패한다")
    void rejectsMissingCongestionLevel() {
        assertThat(validator.validate(request(35.1532, 129.1187, "혼잡한 상황입니다.", null))).isNotEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankOriginalLocation(String originalLocation) {
        ReportCreateReq request = new ReportCreateReq(
                35.1532, 129.1187, "혼잡한 상황입니다.", CongestionLevel.CROWDED,
                null, originalLocation);

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("originalLocation"));
    }

    @Test
    void acceptsOneHundredCharactersAndRejectsLongerOriginalLocation() {
        ReportCreateReq accepted = new ReportCreateReq(
                35.1532, 129.1187, "혼잡한 상황입니다.", CongestionLevel.CROWDED,
                null, "가".repeat(100));
        ReportCreateReq rejected = new ReportCreateReq(
                35.1532, 129.1187, "혼잡한 상황입니다.", CongestionLevel.CROWDED,
                null, "가".repeat(101));

        assertThat(validator.validate(accepted)).isEmpty();
        assertThat(validator.validate(rejected))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("originalLocation"));
    }

    private ReportCreateReq request(
            Double latitude,
            Double longitude,
            String content,
            CongestionLevel congestionLevel
    ) {
        return request(latitude, longitude, content, congestionLevel, null);
    }

    private ReportCreateReq request(
            Double latitude,
            Double longitude,
            String content,
            CongestionLevel congestionLevel,
            String imageUrl
    ) {
        return new ReportCreateReq(
                latitude,
                longitude,
                content,
                congestionLevel,
                imageUrl,
                "메인 무대");
    }
}
