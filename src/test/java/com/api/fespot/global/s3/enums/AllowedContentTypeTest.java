package com.api.fespot.global.s3.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.s3.enums.code.S3ErrorResponseCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AllowedContentTypeTest {

    @ParameterizedTest
    @CsvSource({
            "photo.jpg, .jpg",
            "photo.jpeg, .jpg",
            "photo.png, .png",
            "photo.webp, .webp",
            "photo.heic, .heic"
    })
    void returnsNormalizedExtensionWhenExtensionIsAllowed(String fileName, String expected) {
        assertThat(AllowedContentType.validateExtension(fileName)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "PHOTO.JPG, .jpg",
            "photo.JpEg, .jpg",
            "photo.PNG, .png"
    })
    void validatesExtensionCaseInsensitively(String fileName, String expected) {
        assertThat(AllowedContentType.validateExtension(fileName)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "my.photo.png, .png",
            "photo..jpg, .jpg",
            ".jpg, .jpg"
    })
    void usesLastDotWhenFileNameContainsMultipleDots(String fileName, String expected) {
        assertThat(AllowedContentType.validateExtension(fileName)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"photo", "photo.", "."})
    void throwsWhenFileNameHasNoExtension(String fileName) {
        assertThatThrownBy(() -> AllowedContentType.validateExtension(fileName))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(S3ErrorResponseCode.UNSUPPORTED_CONTENT_TYPE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "malware.exe",
            "page.html",
            "vector.svg",
            "animation.gif",
            "archive.tar.gz",
            "video.mp4"
    })
    void throwsWhenExtensionIsNotAllowed(String fileName) {
        assertThatThrownBy(() -> AllowedContentType.validateExtension(fileName))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(S3ErrorResponseCode.UNSUPPORTED_CONTENT_TYPE));
    }

    // S3 key에 경로 구분자나 공백이 섞인 확장자가 주입되는 것을 막아야 한다.
    @ParameterizedTest
    @ValueSource(strings = {
            "photo.x/malicious",
            "a.png/b",
            "photo.png/../../secret",
            "photo.jpg ",
            "photo. jpg",
            "photo.j pg"
    })
    void throwsWhenExtensionContainsPathSeparatorOrWhitespace(String fileName) {
        assertThatThrownBy(() -> AllowedContentType.validateExtension(fileName))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(S3ErrorResponseCode.UNSUPPORTED_CONTENT_TYPE));
    }

    @Test
    void throwsNullPointerExceptionWhenFileNameIsNull() {
        assertThatThrownBy(() -> AllowedContentType.validateExtension(null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "image/jpeg, JPEG",
            "image/png, PNG",
            "image/webp, WEBP",
            "image/heic, HEIC",
            "IMAGE/JPEG, JPEG"
    })
    void returnsMatchingTypeWhenContentTypeIsAllowed(String contentType, AllowedContentType expected) {
        assertThat(AllowedContentType.from(contentType)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "image/gif",
            "application/pdf",
            "text/html",
            "image/jpeg; charset=utf-8",
            "image/jpg",
            ""
    })
    void throwsWhenContentTypeIsNotAllowed(String contentType) {
        assertThatThrownBy(() -> AllowedContentType.from(contentType))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(S3ErrorResponseCode.UNSUPPORTED_CONTENT_TYPE));
    }

    @Test
    void throwsWhenContentTypeIsNull() {
        assertThatThrownBy(() -> AllowedContentType.from(null))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(S3ErrorResponseCode.UNSUPPORTED_CONTENT_TYPE));
    }
}
