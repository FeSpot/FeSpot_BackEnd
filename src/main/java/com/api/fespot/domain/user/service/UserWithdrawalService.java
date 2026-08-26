package com.api.fespot.domain.user.service;

import com.api.fespot.domain.auth.entity.SocialCredential;
import com.api.fespot.domain.auth.repository.AccessTokenBlacklist;
import com.api.fespot.domain.auth.repository.RefreshSessionStore;
import com.api.fespot.domain.auth.repository.jpa.SocialCredentialRepository;
import com.api.fespot.domain.auth.service.port.SocialUnlinkGateway;
import com.api.fespot.domain.auth.service.social.SocialUnlinkGatewayResolver;
import com.api.fespot.global.security.jwt.JwtProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 회원 탈퇴 오케스트레이션.
 *
 * <p>순서: 제공자 연동 해제(best-effort — Apple 토큰 revoke는 앱스토어 심사 요건, Google revoke·
 * Kakao unlink는 사용자 보호) → DB 삭제(트랜잭션) → 기발급 액세스 토큰 일괄 차단 + 리프레시 세션 전체 폐기.
 * 연동 해제를 삭제보다 먼저 수행하는 이유: 삭제 후 해제가 실패하면 저장했던 토큰·회원번호가 유실되어
 * 재시도가 불가능하지만, 해제 후 삭제가 실패하면 사용자가 재로그인으로 연동을 복구할 수 있다.
 * 연동 해제 실패가 탈퇴를 막아서는 안 되므로(계정 삭제는 항상 가능해야 함) 로그만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final SocialCredentialRepository socialCredentialRepository;
    private final SocialUnlinkGatewayResolver unlinkGatewayResolver;
    private final UserAccountEraser userAccountEraser;
    private final RefreshSessionStore refreshSessionStore;
    private final AccessTokenBlacklist accessTokenBlacklist;
    private final JwtProperties jwtProperties;

    public void withdraw(Long userId) {
        unlinkSocialAccount(userId);
        userAccountEraser.erase(userId);
        revokeIssuedTokens(userId);
        log.info("회원 탈퇴 완료. userId={}", userId);
    }

    private void unlinkSocialAccount(Long userId) {
        socialCredentialRepository.findByUser_Id(userId).ifPresent(credential ->
                unlinkGatewayResolver.resolve(credential.getProvider()).ifPresentOrElse(
                        gateway -> unlink(userId, credential, gateway),
                        () -> log.warn("{} 연동 해제 게이트웨이 없음 — 탈퇴는 계속 진행합니다. userId={}",
                                credential.getProvider(), userId)));
    }

    private void unlink(Long userId, SocialCredential credential, SocialUnlinkGateway gateway) {
        boolean unlinked;
        try {
            unlinked = gateway.unlink(credential.getProviderUserId(), credential.getProviderRefreshToken());
        } catch (RuntimeException exception) {
            log.warn("{} 연동 해제 중 오류 — 탈퇴는 계속 진행합니다. userId={}",
                    credential.getProvider(), userId, exception);
            return;
        }
        if (unlinked) {
            log.info("{} 연동 해제 완료. userId={}", credential.getProvider(), userId);
        } else {
            log.warn("{} 연동 해제 실패 — 탈퇴는 계속 진행합니다. userId={}", credential.getProvider(), userId);
        }
    }

    /**
     * 계정 삭제 후 남은 세션·토큰이 오류를 만들지 않도록 정리한다. 실패해도 탈퇴 결과에는 영향이 없다.
     * 액세스 토큰 차단이 보안상 더 급하므로(리프레시 재발급은 사용자 행이 없어 어차피 실패)
     * 블랙리스트를 먼저, 두 단계를 독립적으로 수행한다.
     */
    private void revokeIssuedTokens(Long userId) {
        try {
            accessTokenBlacklist.blacklistAllUserTokens(
                    userId, Duration.ofSeconds(jwtProperties.getAccessExpiration()));
        } catch (RuntimeException exception) {
            log.error("탈퇴 후 액세스 토큰 차단 실패 — 기발급 토큰이 잔여 수명 동안 유효합니다. userId={}",
                    userId, exception);
        }
        try {
            refreshSessionStore.revokeAll(userId);
        } catch (RuntimeException exception) {
            log.error("탈퇴 후 리프레시 세션 폐기 실패. userId={}", userId, exception);
        }
    }
}
