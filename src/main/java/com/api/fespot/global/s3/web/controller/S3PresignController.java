package com.api.fespot.global.s3.web.controller;

import com.api.fespot.global.response.SuccessResponse;
import com.api.fespot.global.s3.service.S3PresignService;
import com.api.fespot.global.s3.web.dto.S3PresignedUrlRequest;
import com.api.fespot.global.s3.web.dto.S3PresignedUrlResponse;
import com.api.fespot.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class S3PresignController {

    private final S3PresignService s3PresignService;

    // 파일 업로드를 위한 Presign URL 발급 (클라이언트는 이 URL로 S3에 직접 업로드. 콜백 URL을 다시 서버로 전달하여 영속화한다.)
    @PostMapping("/s3/presigned-url")
    public ResponseEntity<SuccessResponse<S3PresignedUrlResponse>> createPresignedUrl(
            @AuthenticationPrincipal AuthPrincipal authPrincipal,
            @Valid @RequestBody S3PresignedUrlRequest request
    ) {
        Map<String, String> mappedUrl = s3PresignService.createPresignedUrl(request.category(), request.fileName(), request.contentType());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SuccessResponse.create(S3PresignedUrlResponse.from(mappedUrl)));
    }
}
