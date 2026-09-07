package com.api.fespot.global.s3.web.dto;

import java.util.Map;

public record S3PresignedUrlResponse (

    // presigned url
    String presignedUrl,

    // actual url
    String fileUrl
) {

    public static S3PresignedUrlResponse from(Map<String, String> mappedUrl) {
        if (!mappedUrl.containsKey("presignedUrl") || !mappedUrl.containsKey("actualFileUrl")) {
            throw new IllegalArgumentException("본 객체 생성 메소드가 기대하는 Map 양식이 아닙니다.");
        }

        return new S3PresignedUrlResponse(mappedUrl.get("presignedUrl"), mappedUrl.get("actualFileUrl"));
    }
}
