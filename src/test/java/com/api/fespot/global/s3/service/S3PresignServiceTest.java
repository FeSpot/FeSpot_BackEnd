package com.api.fespot.global.s3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.s3.enums.MediaCategory;
import com.api.fespot.global.s3.enums.code.S3ErrorResponseCode;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class S3PresignServiceTest {

    private static final String BUCKET = "fespot-bucket";
    private static final String REGION = "ap-northeast-2";
    private static final String PRESIGNED_URL = "https://fespot-bucket.s3.ap-northeast-2.amazonaws.com/presigned";
    private static final String KEY_PATTERN =
            "report/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final S3Presigner s3Presigner = mock(S3Presigner.class);
    private final S3PresignService s3PresignService = new S3PresignService(s3Presigner);

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(s3PresignService, "bucket", BUCKET);
        ReflectionTestUtils.setField(s3PresignService, "region", REGION);

        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        URL url = URI.create(PRESIGNED_URL).toURL();
        given(presignedRequest.url()).willReturn(url);
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .willReturn(presignedRequest);
    }

    @Test
    void returnsPresignedUrlAndActualFileUrl() {
        Map<String, String> result =
                s3PresignService.createPresignedUrl(MediaCategory.REPORT, "photo.jpg", "image/jpeg");

        assertThat(result.get("presignedUrl")).isEqualTo(PRESIGNED_URL);
        assertThat(result.get("actualFileUrl"))
                .matches("https://" + BUCKET + "\\.s3\\." + REGION + "\\.amazonaws\\.com/"
                        + KEY_PATTERN + "\\.jpg");
    }

    @Test
    void generatesKeyWithCategoryPrefixAndUuidAndNormalizedExtension() {
        s3PresignService.createPresignedUrl(MediaCategory.REPORT, "photo.JPEG", "image/jpeg");

        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        assertThat(captor.getValue().putObjectRequest().key())
                .matches(KEY_PATTERN + "\\.jpg");
    }

    @Test
    void generatesUniqueKeyForSameFileName() {
        s3PresignService.createPresignedUrl(MediaCategory.REPORT, "photo.jpg", "image/jpeg");
        s3PresignService.createPresignedUrl(MediaCategory.REPORT, "photo.jpg", "image/jpeg");

        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner, times(2)).presignPutObject(captor.capture());
        assertThat(captor.getAllValues().get(0).putObjectRequest().key())
                .isNotEqualTo(captor.getAllValues().get(1).putObjectRequest().key());
    }

    @Test
    void requestsPresignWithBucketContentTypeAndExpiration() {
        s3PresignService.createPresignedUrl(MediaCategory.REPORT, "photo.png", "image/png");

        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        assertThat(captor.getValue().putObjectRequest().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().putObjectRequest().contentType()).isEqualTo("image/png");
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void throwsWithoutCallingPresignerWhenExtensionIsNotAllowed() {
        assertThatThrownBy(() ->
                s3PresignService.createPresignedUrl(MediaCategory.REPORT, "malware.exe", "image/jpeg"))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(S3ErrorResponseCode.UNSUPPORTED_CONTENT_TYPE));

        verifyNoInteractions(s3Presigner);
    }

    @Test
    void throwsWithoutCallingPresignerWhenFileNameContainsPathInjection() {
        assertThatThrownBy(() ->
                s3PresignService.createPresignedUrl(
                        MediaCategory.REPORT, "photo.png/../../secret", "image/png"))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(S3ErrorResponseCode.UNSUPPORTED_CONTENT_TYPE));

        verifyNoInteractions(s3Presigner);
    }
}
