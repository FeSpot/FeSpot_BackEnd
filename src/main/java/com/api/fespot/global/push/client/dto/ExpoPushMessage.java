package com.api.fespot.global.push.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.util.StringUtils;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpoPushMessage(
        String to,
        String title,
        String body,
        Map<String, Object> data,
        String sound,
        String channelId,
        Integer badge
) {

    public ExpoPushMessage{
        if(!StringUtils.hasText(to)){
            throw new IllegalArgumentException("Expo push token은 비어있을 수 없습니다.");
        }

        data = data == null ? Map.of() : Map.copyOf(data);
    }

    /* 실사용 예시
    ExpoPushMessage message = new ExpoPushMessage(
        expoPushToken,
        "축제 혼잡 알림",
        "현재 행사장 주변이 혼잡합니다.",
        Map.of(
                "notificationId", 123L,
                "deeplink", "/festivals/45"
        ),
        "default",
        "default",
        3
);
     */
}
