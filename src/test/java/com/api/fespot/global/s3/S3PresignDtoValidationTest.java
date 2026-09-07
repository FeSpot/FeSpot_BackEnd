package com.api.fespot.global.s3;

import com.api.fespot.global.s3.enums.MediaCategory;
import com.api.fespot.global.s3.web.dto.S3PresignedUrlRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class S3PresignDtoValidationTest {

    private Validator validator;

    private final MediaCategory category = MediaCategory.REPORT;
    private final String fileName = "photo.jpg";
    private final String contentType = "image/jpeg";

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("모든 필드가 유효하면 검증을 통과한다.")
    void passesWhenAllFieldsAreValid() {

        // given
        S3PresignedUrlRequest request = new S3PresignedUrlRequest(category, fileName, contentType);

        // when
        Set<ConstraintViolation<S3PresignedUrlRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("카테고리를 null값으로 넘기면 실패한다.")
    void failsWhenCategoryIsNull() {

        // given
        S3PresignedUrlRequest request = new S3PresignedUrlRequest(null, fileName, contentType);

        // when
        Set<ConstraintViolation<S3PresignedUrlRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("파일명을 null값으로 넘기면 실패한다.")
    void failsWhenFileNameIsNull() {

        // given
        S3PresignedUrlRequest request = new S3PresignedUrlRequest(category, null, contentType);

        // when
        Set<ConstraintViolation<S3PresignedUrlRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("파일명을 빈 문자열로 넘기면 NotBlank와 Length 검증이 모두 실패한다.")
    void failsWhenFileNameIsEmpty() {

        // given
        S3PresignedUrlRequest request = new S3PresignedUrlRequest(category, "", contentType);

        // when
        Set<ConstraintViolation<S3PresignedUrlRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).hasSize(2);
    }

    @Test
    @DisplayName("파일명을 공백만으로 넘기면 실패한다.")
    void failsWhenFileNameIsBlank() {

        // given
        S3PresignedUrlRequest request = new S3PresignedUrlRequest(category, " ", contentType);

        // when
        Set<ConstraintViolation<S3PresignedUrlRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("파일명이 50자를 초과하면 실패한다.")
    void failsWhenFileNameExceedsMaxLength() {

        // given
        String longFileName = "a".repeat(47) + ".jpg";   // 51자
        S3PresignedUrlRequest request = new S3PresignedUrlRequest(category, longFileName, contentType);

        // when
        Set<ConstraintViolation<S3PresignedUrlRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("파일명이 정확히 50자면 통과한다.")
    void passesWhenFileNameIsExactlyMaxLength() {

        // given
        String maxFileName = "a".repeat(46) + ".jpg";   // 50자
        S3PresignedUrlRequest request = new S3PresignedUrlRequest(category, maxFileName, contentType);

        // when
        Set<ConstraintViolation<S3PresignedUrlRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("콘텐츠 타입을 null값으로 넘기면 실패한다.")
    void failsWhenContentTypeIsNull() {

        // given
        S3PresignedUrlRequest request = new S3PresignedUrlRequest(category, fileName, null);

        // when
        Set<ConstraintViolation<S3PresignedUrlRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).hasSize(1);
    }

    @Test
    @DisplayName("콘텐츠 타입을 빈 문자열로 넘기면 실패한다.")
    void failsWhenContentTypeIsBlank() {

        // given
        S3PresignedUrlRequest request = new S3PresignedUrlRequest(category, fileName, "");

        // when
        Set<ConstraintViolation<S3PresignedUrlRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).hasSize(1);
    }
}
