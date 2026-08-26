package com.api.fespot.domain.auth.repository.redis;

import com.api.fespot.domain.auth.repository.AccessTokenBlacklist;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisAccessTokenBlacklist implements AccessTokenBlacklist {

    private static final String TOKEN_KEY_PREFIX = "auth:blacklist:token:";
    private static final String USER_KEY_PREFIX = "auth:blacklist:user:";
    private static final String BLACKLISTED = "1";

    private final StringRedisTemplate redisTemplate;

    public RedisAccessTokenBlacklist(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void add(String accessToken, Duration ttl) {
        validateTtl(ttl);
        redisTemplate.opsForValue().set(tokenKey(accessToken), BLACKLISTED, ttl);
    }

    @Override
    public void blacklistAllUserTokens(Long userId, Duration ttl) {
        validateTtl(ttl);
        redisTemplate.opsForValue().set(
                userKey(userId),
                Long.toString(System.currentTimeMillis()),
                ttl);
    }

    @Override
    public boolean contains(String accessToken) {
        return redisTemplate.hasKey(tokenKey(accessToken));
    }

    @Override
    public boolean isUserTokenBlacklisted(Long userId, Instant issuedAt) {
        String cutoff = redisTemplate.opsForValue().get(userKey(userId));
        if (cutoff == null) {
            return false;
        }
        // JWT iat는 초 단위로 절사되므로 컷오프와 같은 초에 발급된 정상 토큰이 과차단될 수 있다(최대 1초).
        // 비교를 <로 완화하면 같은 초에 발급된 탈취 토큰이 통과하므로, <= 과차단이 의도된 fail-closed다.
        return issuedAt.toEpochMilli() <= parseCutoff(cutoff, userId);
    }

    private long parseCutoff(String cutoff, Long userId) {
        try {
            return Long.parseLong(cutoff);
        } catch (NumberFormatException exception) {
            // 컷오프 값이 손상됐다면 500 대신 해당 사용자의 모든 토큰을 차단한다(fail-closed).
            log.error("Corrupted user blacklist cutoff. Blocking all tokens. userId={}", userId);
            return Long.MAX_VALUE;
        }
    }

    private String tokenKey(String accessToken) {
        return TOKEN_KEY_PREFIX + fingerprint(accessToken);
    }

    private String userKey(Long userId) {
        return USER_KEY_PREFIX + userId;
    }

    private void validateTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Blacklist TTL must be positive");
        }
    }

    private String fingerprint(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
