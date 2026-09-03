package com.api.fespot.domain.notification.web.controller;

import com.api.fespot.domain.notification.service.NotificationService;
import com.api.fespot.domain.notification.web.dto.PushTokenReq;
import com.api.fespot.global.response.SuccessResponse;
import com.api.fespot.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me/push-tokens")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Expo Push Token 등록")
    @PutMapping
    public ResponseEntity<SuccessResponse<?>> register(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PushTokenReq request
    ) {
        notificationService.register(principal.getId(), request.token());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.emptyCustom("푸시 토큰이 등록되었습니다."));
    }

    @Operation(summary = "Expo Push Token 삭제")
    @DeleteMapping
    public ResponseEntity<SuccessResponse<?>> unregister(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PushTokenReq request
    ) {
        notificationService.unregister(principal.getId(), request.token());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(SuccessResponse.emptyCustom("푸시 토큰이 삭제되었습니다."));
    }
}