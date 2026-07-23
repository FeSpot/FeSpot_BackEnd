package com.api.fespot.domain.festival.entity;

import java.util.regex.Pattern;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FestivalKey {

    //홈에서 찾을 축제를 정의
    BUSAN_FIREWORKS_FESTIVAL("부산바다축제"),
    BUSAN_INTERNATIONAL_FILM_FESTIVAL("부산국제영화제"),
    BUSAN_INTERNATIONAL_ROCK_FESTIVAL("부산국제록페스티벌");

    private static final Pattern EDITION_PREFIX = Pattern.compile("^\\(?제\\s*\\d+회\\)?\\s*");

    private final String displayName;

    public boolean matches(String title) {
        if (title == null) {
            return false;
        }
        String normalizedTitle = EDITION_PREFIX.matcher(title.strip()).replaceFirst("");
        return normalizedTitle.equals(displayName);
    }
}
