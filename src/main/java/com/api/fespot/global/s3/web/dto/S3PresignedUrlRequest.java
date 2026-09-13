package com.api.fespot.global.s3.web.dto;

import com.api.fespot.global.s3.enums.MediaCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Length;

public record S3PresignedUrlRequest(

        @NotNull(message = "카테고리는 필수값입니다.")
        MediaCategory category,

        @NotBlank(message = "파일명은 필수값입니다.")
        @Length(min = 1, max = 50, message = "파일명은 1자 이상, 50자 미만이어야 합니다.")
        String fileName,

        @NotBlank(message = "콘텐츠 타입은 필수값입니다.")
        String contentType
) {
}
