package com.api.fespot.domain.auth.repository.redis;

import com.api.fespot.domain.auth.repository.RefreshSessionStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Component
public class RedisRefreshSessionStore implements RefreshSessionStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    private static final DefaultRedisScript<Long> REGISTER_SCRIPT = script("""
            local generation = redis.call('GET', KEYS[2])
            if generation then
                if generation ~= '0' and not string.match(generation, '^[1-9]%d*$') then
                    return redis.error_reply('invalid refresh generation')
                end
            else
                generation = '0'
                redis.call('SET', KEYS[2], generation)
            end
            redis.call('SET', KEYS[1], 'ACTIVE:' .. ARGV[1] .. ':' .. generation, 'PX', ARGV[2])
            return 1
            """);

    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = script("""
            local record = redis.call('GET', KEYS[1])
            if not record then
                return 0
            end

            local state, recordUserId, tokenGeneration = string.match(record, '^([^:]+):([^:]+):([^:]+)$')
            if not state or recordUserId ~= ARGV[1] then
                return 0
            end
            if tokenGeneration ~= '0' and not string.match(tokenGeneration, '^[1-9]%d*$') then
                return 0
            end
            if state == 'REVOKED' then
                return 2
            end
            if state == 'USED' then
                local currentGeneration = redis.call('GET', KEYS[3]) or '0'
                if currentGeneration ~= '0'
                        and not string.match(currentGeneration, '^[1-9]%d*$') then
                    return 0
                end
                if tokenGeneration == currentGeneration then
                    redis.call('INCR', KEYS[3])
                end
                return 3
            end
            if state ~= 'ACTIVE' then
                return 0
            end

            local currentGeneration = redis.call('GET', KEYS[3]) or '0'
            if currentGeneration ~= '0'
                    and not string.match(currentGeneration, '^[1-9]%d*$') then
                return 0
            end
            local currentTtl = redis.call('PTTL', KEYS[1])
            if currentTtl <= 0 then
                return 0
            end
            if tokenGeneration ~= currentGeneration then
                redis.call('SET', KEYS[1], 'REVOKED:' .. recordUserId .. ':' .. tokenGeneration,
                        'PX', currentTtl)
                return 2
            end

            redis.call('SET', KEYS[1], 'USED:' .. recordUserId .. ':' .. tokenGeneration,
                    'PX', currentTtl)
            redis.call('SET', KEYS[2], 'ACTIVE:' .. recordUserId .. ':' .. tokenGeneration,
                    'PX', ARGV[2])
            return 1
            """);

    private static final DefaultRedisScript<Long> REVOKE_SCRIPT = script("""
            local record = redis.call('GET', KEYS[1])
            if not record then
                return 0
            end

            local state, recordUserId, tokenGeneration = string.match(record, '^([^:]+):([^:]+):([^:]+)$')
            if not state or recordUserId ~= ARGV[1] then
                return 0
            end
            if tokenGeneration ~= '0' and not string.match(tokenGeneration, '^[1-9]%d*$') then
                return 0
            end
            if state ~= 'ACTIVE' and state ~= 'USED' and state ~= 'REVOKED' then
                return 0
            end
            if state == 'ACTIVE' then
                local ttl = redis.call('PTTL', KEYS[1])
                if ttl > 0 then
                    redis.call('SET', KEYS[1], 'REVOKED:' .. recordUserId .. ':' .. tokenGeneration,
                            'PX', ttl)
                end
            end
            if state == 'USED' then
                local currentGeneration = redis.call('GET', KEYS[2]) or '0'
                if currentGeneration ~= '0'
                        and not string.match(currentGeneration, '^[1-9]%d*$') then
                    return redis.error_reply('invalid refresh generation')
                end
                if tokenGeneration == currentGeneration then
                    redis.call('INCR', KEYS[2])
                end
            end
            return 1
            """);

    private static final DefaultRedisScript<Long> REVOKE_ALL_SCRIPT = script("""
            local currentGeneration = redis.call('GET', KEYS[1])
            if currentGeneration and currentGeneration ~= '0'
                    and not string.match(currentGeneration, '^[1-9]%d*$') then
                return redis.error_reply('invalid refresh generation')
            end
            return redis.call('INCR', KEYS[1])
            """);

    private final StringRedisTemplate redisTemplate;

    public RedisRefreshSessionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void register(String refreshToken, Long userId, Duration ttl) {
        Long result = redisTemplate.execute(
                REGISTER_SCRIPT,
                List.of(tokenKey(userId, refreshToken), generationKey(userId)),
                userId.toString(),
                ttlMillis(ttl)
        );
        if (result == null || result != 1L) {
            throw new IllegalStateException("Failed to register refresh session");
        }
    }

    @Override
    public RotationOutcome rotate(
            String currentRefreshToken,
            String replacementRefreshToken,
            Long userId,
            Duration replacementTtl
    ) {
        if (Objects.equals(currentRefreshToken, replacementRefreshToken)) {
            throw new IllegalArgumentException("Current and replacement refresh tokens must be different");
        }
        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(
                        tokenKey(userId, currentRefreshToken),
                        tokenKey(userId, replacementRefreshToken),
                        generationKey(userId)
                ),
                userId.toString(),
                ttlMillis(replacementTtl)
        );

        if (result == null) {
            return RotationOutcome.UNKNOWN;
        }
        if (result == 1L) {
            return RotationOutcome.ROTATED;
        }
        if (result == 2L) {
            return RotationOutcome.REVOKED;
        }
        if (result == 3L) {
            return RotationOutcome.REUSED;
        }
        return RotationOutcome.UNKNOWN;
    }

    @Override
    public void revoke(String refreshToken, Long userId) {
        Long result = redisTemplate.execute(
                REVOKE_SCRIPT,
                List.of(tokenKey(userId, refreshToken), generationKey(userId)),
                userId.toString()
        );
        if (result == null || (result != 0L && result != 1L)) {
            throw new IllegalStateException("Failed to revoke refresh session");
        }
    }

    @Override
    public void revokeAll(Long userId) {
        Long result = redisTemplate.execute(
                REVOKE_ALL_SCRIPT,
                List.of(generationKey(userId))
        );
        if (result == null || result <= 0L) {
            throw new IllegalStateException("Failed to revoke all refresh sessions");
        }
    }

    private String tokenKey(Long userId, String refreshToken) {
        return KEY_PREFIX + "{" + userId + "}:token:" + fingerprint(refreshToken);
    }

    private String generationKey(Long userId) {
        return KEY_PREFIX + "{" + userId + "}:generation";
    }

    private String fingerprint(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String ttlMillis(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Refresh session TTL must be positive");
        }
        long millis = ttl.toMillis();
        if (millis == 0L) {
            throw new IllegalArgumentException("Refresh session TTL must be at least one millisecond");
        }
        return Long.toString(millis);
    }

    private static DefaultRedisScript<Long> script(String source) {
        return new DefaultRedisScript<>(source, Long.class);
    }
}
