package com.api.fespot.global.cache;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@Getter
@RequiredArgsConstructor
public enum CaffeineCacheDefinition {
    DAILY_FESTIVAL_FORECAST("dailyFestivalForecast", Duration.ofMinutes(5), 50),
    FESTIVAL_AREA_FORECAST("festivalAreaForecast", Duration.ofMinutes(5), 50);

    private final String cacheName;
    private final Duration expireAfterWrite; //항목이 생성된 후 또는 해당 값이 바뀐 후 특정 기간이 지나면 각 항목이 캐시에서 자동으로 없어지도록 설정
    private final int maximumSize; //캐시에 포함될 수 있 최대 엔트리 수
}
