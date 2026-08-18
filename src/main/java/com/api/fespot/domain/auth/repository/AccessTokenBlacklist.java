package com.api.fespot.domain.auth.repository;

import java.time.Duration;
import java.time.Instant;

/**
 * 로그아웃했거나 탈취가 의심되는 액세스 토큰의 잔여 수명 동안의 차단 목록.
 * 구현체는 토큰 원문 대신 지문(해시)을 저장해야 한다.
 */
public interface AccessTokenBlacklist {

    void add(String accessToken, Duration ttl);

    /**
     * 호출 시점 이전에 발급된 해당 사용자의 모든 액세스 토큰을 차단한다.
     * 리프레시 토큰 재사용(탈취 의심) 감지 시 사용한다.
     */
    void blacklistAllUserTokens(Long userId, Duration ttl);

    boolean contains(String accessToken);

    /** 해당 발급 시각의 사용자 토큰이 전체 차단 경계에 걸리는지 확인한다. */
    boolean isUserTokenBlacklisted(Long userId, Instant issuedAt);
}
