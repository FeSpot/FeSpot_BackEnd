package com.api.fespot.domain.auth.service;

import com.api.fespot.domain.auth.exception.InvalidRefreshTokenException;
import com.api.fespot.domain.auth.exception.RefreshTokenReusedException;
import com.api.fespot.domain.auth.repository.AccessTokenBlacklist;
import com.api.fespot.domain.auth.repository.RefreshSessionStore;
import com.api.fespot.domain.auth.repository.RefreshSessionStore.RotationOutcome;
import com.api.fespot.domain.auth.service.port.AuthTokenIssuer;
import com.api.fespot.domain.auth.service.port.AuthTokenIssuer.IssuedAuthTokens;
import com.api.fespot.domain.auth.web.dto.LoginRes;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.security.jwt.JwtProperties;
import com.api.fespot.global.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;
    private final AuthTokenIssuer authTokenIssuer;
    private final RefreshSessionStore refreshSessionStore;
    private final AccessTokenBlacklist accessTokenBlacklist;

    /**
     * 리프레시 토큰을 검증하고 원자적으로 로테이션하여 새 토큰 쌍을 발급한다.
     * 이미 사용된 토큰이 재사용되면 탈취로 간주하고 해당 사용자의 모든 세션을 무효화한다.
     */
    public LoginRes reissue(String refreshToken) {
        Long userId = parseUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidRefreshTokenException::new);

        IssuedAuthTokens tokens = authTokenIssuer.issue(user);
        RotationOutcome outcome = refreshSessionStore.rotate(
                refreshToken,
                tokens.refreshToken(),
                userId,
                tokens.refreshTokenTtl());

        return switch (outcome) {
            case ROTATED -> LoginRes.bearer(tokens.accessToken(), tokens.refreshToken());
            case REUSED -> {
                // 재사용 감지 시점 이전에 발급된 액세스 토큰도 잔여 수명 동안 전부 차단한다.
                accessTokenBlacklist.blacklistAllUserTokens(
                        userId, Duration.ofSeconds(jwtProperties.getAccessExpiration()));
                log.warn("Refresh token reuse detected. Refresh sessions and issued access tokens revoked. userId={}",
                        userId);
                throw new RefreshTokenReusedException();
            }
            case REVOKED, UNKNOWN -> throw new InvalidRefreshTokenException();
        };
    }

    /**
     * 리프레시 세션을 폐기하고, 남은 수명 동안 액세스 토큰을 블랙리스트에 올려
     * 로그아웃 이후 두 토큰 모두 재사용될 수 없게 한다.
     */
    public void logout(String accessToken, String refreshToken) {
        Long userId = jwtTokenProvider.getUserIdFromToken(accessToken);
        refreshSessionStore.revoke(refreshToken, userId);

        long remainingMillis = jwtTokenProvider.getExpiration(accessToken);
        if (remainingMillis > 0) {
            accessTokenBlacklist.add(accessToken, Duration.ofMillis(remainingMillis));
        }
    }

    private Long parseUserId(String refreshToken) {
        try {
            return jwtTokenProvider.getUserIdFromRefreshToken(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidRefreshTokenException();
        }
    }
}
