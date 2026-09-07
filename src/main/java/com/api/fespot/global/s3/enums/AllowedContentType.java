package com.api.fespot.global.s3.enums;

import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.s3.enums.code.S3ErrorResponseCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.Set;

@Getter
public enum AllowedContentType {

    JPEG("image/jpeg", ".jpg", ".jpeg"),
    PNG("image/png", ".png"),
    WEBP("image/webp", ".webp"),
    HEIC("image/heic", ".heic"),
    ;

    private final String contentType;
    private final String extension;
    private final Set<String> allowedExtensions;

    AllowedContentType(String contentType, String... extensions) {
        this.contentType = contentType;
        this.extension = extensions[0];
        this.allowedExtensions = Set.of(extensions);
    }

    public static AllowedContentType from(String contentType) {
        return Arrays.stream(values())
                .filter(type -> type.contentType.equalsIgnoreCase(contentType))
                .findFirst()
                .orElseThrow(() -> new CustomException(S3ErrorResponseCode.UNSUPPORTED_CONTENT_TYPE));
    }

    // 파일명에서 확장자를 추출해 허용 목록으로 검증하고, 정규화된 확장자(예: ".jpeg" -> ".jpg")를 반환한다.
    public static String validateExtension(String fileName) {
        String extension = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.')).toLowerCase()
                : "";

        return Arrays.stream(values())
                .filter(type -> type.allowedExtensions.contains(extension))
                .findFirst()
                .map(AllowedContentType::getExtension)
                .orElseThrow(() -> new CustomException(S3ErrorResponseCode.UNSUPPORTED_CONTENT_TYPE));
    }
}
