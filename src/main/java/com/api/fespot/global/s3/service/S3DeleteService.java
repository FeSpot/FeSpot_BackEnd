package com.api.fespot.global.s3.service;

import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.s3.enums.code.S3ErrorResponseCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.net.URI;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3DeleteService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.region}")
    private String region;

    // DB에 저장된 actualFileUrl로 S3 객체를 삭제한다. 존재하지 않는 객체 삭제는 S3가 성공으로 처리하므로 재실행에 안전하다.
    public void deleteByFileUrl(String fileUrl) {
        String key = extractKey(fileUrl);

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (SdkException e) {
            log.error("S3 객체 삭제 실패. key={}, cause={}: {}",
                    key, e.getClass().getSimpleName(), e.getMessage());
            throw new CustomException(S3ErrorResponseCode.S3_DELETE_FAILED);
        }
    }

    private String extractKey(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new CustomException(S3ErrorResponseCode.INVALID_FILE_URL);
        }

        URI uri;
        try {
            uri = URI.create(fileUrl);
        } catch (IllegalArgumentException e) {
            throw new CustomException(S3ErrorResponseCode.INVALID_FILE_URL);
        }

        String expectedHost = String.format("%s.s3.%s.amazonaws.com", bucket, region);
        if (!expectedHost.equals(uri.getHost())) {
            throw new CustomException(S3ErrorResponseCode.INVALID_FILE_URL);
        }

        String path = uri.getPath();
        if (path == null || path.length() <= 1) {
            throw new CustomException(S3ErrorResponseCode.INVALID_FILE_URL);
        }

        return path.substring(1);
    }
}
