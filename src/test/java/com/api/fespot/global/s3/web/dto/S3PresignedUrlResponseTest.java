package com.api.fespot.global.s3.web.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class S3PresignedUrlResponseTest {

    private static final String PRESIGNED_URL = "https://bucket.s3.region.amazonaws.com/key?signature";
    private static final String ACTUAL_FILE_URL = "https://bucket.s3.region.amazonaws.com/key";

    @Test
    void mapsPresignedUrlAndActualFileUrl() {
        Map<String, String> mappedUrl = Map.of(
                "presignedUrl", PRESIGNED_URL,
                "actualFileUrl", ACTUAL_FILE_URL
        );

        S3PresignedUrlResponse response = S3PresignedUrlResponse.from(mappedUrl);

        assertThat(response.presignedUrl()).isEqualTo(PRESIGNED_URL);
        assertThat(response.fileUrl()).isEqualTo(ACTUAL_FILE_URL);
    }

    @Test
    void throwsWhenPresignedUrlKeyIsMissing() {
        Map<String, String> mappedUrl = Map.of("actualFileUrl", ACTUAL_FILE_URL);

        assertThatThrownBy(() -> S3PresignedUrlResponse.from(mappedUrl))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenActualFileUrlKeyIsMissing() {
        Map<String, String> mappedUrl = Map.of("presignedUrl", PRESIGNED_URL);

        assertThatThrownBy(() -> S3PresignedUrlResponse.from(mappedUrl))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenMapIsEmpty() {
        assertThatThrownBy(() -> S3PresignedUrlResponse.from(Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
