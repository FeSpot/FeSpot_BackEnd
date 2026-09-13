package com.api.fespot.global.s3.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.s3.enums.code.S3ErrorResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3DeleteServiceTest {

    private static final String BUCKET = "fespot-bucket";
    private static final String REGION = "ap-northeast-2";
    private static final String FILE_URL =
            "https://fespot-bucket.s3.ap-northeast-2.amazonaws.com/report/uuid.jpg";

    private final S3Client s3Client = mock(S3Client.class);
    private final S3DeleteService s3DeleteService = new S3DeleteService(s3Client);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(s3DeleteService, "bucket", BUCKET);
        ReflectionTestUtils.setField(s3DeleteService, "region", REGION);
    }

    @Test
    void deletesObjectWithBucketAndExtractedKey() {
        s3DeleteService.deleteByFileUrl(FILE_URL);

        ArgumentCaptor<DeleteObjectRequest> captor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("report/uuid.jpg");
    }

    @Test
    void succeedsWhenObjectDoesNotExist() {
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willReturn(DeleteObjectResponse.builder().build());

        assertThatCode(() -> s3DeleteService.deleteByFileUrl(FILE_URL))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", ""})
    void throwsWhenFileUrlIsBlank(String fileUrl) {
        assertThatThrownBy(() -> s3DeleteService.deleteByFileUrl(fileUrl))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(S3ErrorResponseCode.INVALID_FILE_URL));

        verifyNoInteractions(s3Client);
    }

    @Test
    void throwsWhenFileUrlIsNull() {
        assertThatThrownBy(() -> s3DeleteService.deleteByFileUrl(null))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(S3ErrorResponseCode.INVALID_FILE_URL));

        verifyNoInteractions(s3Client);
    }

    // 다른 버킷·도메인을 향한 URL로는 삭제를 시도하지 않아야 한다.
    @ParameterizedTest
    @ValueSource(strings = {
            "https://other-bucket.s3.ap-northeast-2.amazonaws.com/report/uuid.jpg",
            "https://fespot-bucket.s3.us-east-1.amazonaws.com/report/uuid.jpg",
            "https://evil.example.com/report/uuid.jpg",
            "https://fespot-bucket.s3.ap-northeast-2.amazonaws.com.evil.com/report/uuid.jpg"
    })
    void throwsWhenHostDoesNotMatchBucketAndRegion(String fileUrl) {
        assertThatThrownBy(() -> s3DeleteService.deleteByFileUrl(fileUrl))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(S3ErrorResponseCode.INVALID_FILE_URL));

        verifyNoInteractions(s3Client);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://fespot-bucket.s3.ap-northeast-2.amazonaws.com",
            "https://fespot-bucket.s3.ap-northeast-2.amazonaws.com/",
            "https://bad url/report/uuid.jpg"
    })
    void throwsWhenFileUrlHasNoKeyOrIsMalformed(String fileUrl) {
        assertThatThrownBy(() -> s3DeleteService.deleteByFileUrl(fileUrl))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(S3ErrorResponseCode.INVALID_FILE_URL));

        verifyNoInteractions(s3Client);
    }

    @Test
    void throwsDeleteFailedWhenSdkExceptionOccurs() {
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willThrow(S3Exception.builder().message("Access Denied").build());

        assertThatThrownBy(() -> s3DeleteService.deleteByFileUrl(FILE_URL))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(S3ErrorResponseCode.S3_DELETE_FAILED));
    }
}
