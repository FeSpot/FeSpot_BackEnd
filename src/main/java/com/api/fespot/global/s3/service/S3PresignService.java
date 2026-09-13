package com.api.fespot.global.s3.service;

import com.api.fespot.global.s3.enums.AllowedContentType;
import com.api.fespot.global.s3.enums.MediaCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3PresignService {

    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(5);

    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.region}")
    private String region;

    /**
     *
     * @param category 미리 지정한 디렉토리 하위로 저장할 수 있도록 카테고리를 지정해야 합니다.
     * @param fileName 고유한 파일
     * @param contentType
     * @return
     */
    public Map<String, String> createPresignedUrl(MediaCategory category, String fileName, String contentType) {
        String key = generateKey(category, fileName);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_EXPIRATION)
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        String fileUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, key);

        return mappedUploadUrlWithActualUrl(presignedRequest.url().toString(), fileUrl);
    }

    private String generateKey(MediaCategory category, String fileName) {
        String extension = AllowedContentType.validateExtension(fileName);

        return category.name().toLowerCase() + "/" + UUID.randomUUID() + extension;
    }

    private Map<String, String> mappedUploadUrlWithActualUrl(String presignedUrl, String actualFileUrl) {
        Map<String, String> result = new HashMap<>();

        result.put("presignedUrl", presignedUrl);
        result.put("actualFileUrl", actualFileUrl);

        return result;
    }
}
