package com.api.fespot.domain.auth.service.social;

import com.api.fespot.domain.auth.exception.DuplicateEmailException;
import com.api.fespot.domain.auth.exception.InvalidOAuthTokenException;
import com.api.fespot.domain.auth.repository.RefreshSessionStore;
import com.api.fespot.domain.auth.repository.SocialAccountPort;
import com.api.fespot.domain.auth.service.port.AuthTokenIssuer;
import com.api.fespot.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
public class SocialLoginService implements SocialLoginUseCase {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
    );

    private final OAuthClientResolver clientResolver;
    private final SocialAccountPort socialAccountPort;
    private final AuthTokenIssuer tokenIssuer;
    private final RefreshSessionStore refreshSessionStore;
    private final SocialUnlinkGatewayResolver unlinkGatewayResolver;

    @Override
    public SocialLoginResult login(SocialLoginCommand command) {
        OAuthClient client = clientResolver.resolve(command.provider());
        OAuthIdentity identity = command.expectedNonce() == null
                ? client.authenticate(command.credential())
                : client.authenticate(command.credential(), command.expectedNonce());
        validateLookupIdentity(command, identity);

        User user = socialAccountPort.findUser(identity.provider(), identity.providerUserId())
                .orElseGet(() -> register(identity));
        storeProviderRefreshToken(command, identity);
        AuthTokenIssuer.IssuedAuthTokens tokens = tokenIssuer.issue(user);
        registerRefreshSession(tokens, user.getId());
        return new SocialLoginResult(tokens.accessToken(), tokens.refreshToken());
    }

    /**
     * authorization code가 함께 오면 제공자 refresh token으로 교환해 보관한다(Apple/Google).
     * 탈퇴 시 제공자 연동 해제(Apple은 앱스토어 심사 요건)에 쓰이며, 실패해도 로그인은 막지 않는다.
     * 이미 보관된 토큰이 있으면 로그인 hot path에서의 제공자 API 왕복을 생략한다
     * (특히 Google은 최초 동의 때만 refresh token을 내려주므로 재로그인 교환은 항상 헛걸음이다).
     */
    private void storeProviderRefreshToken(SocialLoginCommand command, OAuthIdentity identity) {
        if (isBlank(command.authorizationCode())) {
            return;
        }
        unlinkGatewayResolver.resolve(command.provider()).ifPresent(gateway -> {
            if (!gateway.usesStoredRefreshToken()) {
                return;
            }
            try {
                if (socialAccountPort.hasProviderRefreshToken(command.provider(), identity.providerUserId())) {
                    return;
                }
                gateway.exchangeCodeForRefreshToken(command.authorizationCode(), identity.providerUserId())
                        .ifPresentOrElse(
                                refreshToken -> socialAccountPort.storeProviderRefreshToken(
                                        command.provider(), identity.providerUserId(), refreshToken),
                                () -> log.warn("{} refresh token 확보 실패 — 탈퇴 시 연동 해제가 제한될 수 있습니다.",
                                        command.provider()));
            } catch (RuntimeException exception) {
                log.warn("{} refresh token 저장 실패 — 탈퇴 시 연동 해제가 제한될 수 있습니다.",
                        command.provider(), exception);
            }
        });
    }

    private void validateLookupIdentity(SocialLoginCommand command, OAuthIdentity identity) {
        if (identity == null
                || identity.provider() != command.provider()
                || isBlank(identity.providerUserId())
                || identity.providerUserId().length() > 255) {
            throw new InvalidOAuthTokenException();
        }
    }

    private User register(OAuthIdentity identity) {
        validateRegistrationIdentity(identity);
        try {
            if (socialAccountPort.existsUserByEmail(identity.email())) {
                throw new DuplicateEmailException();
            }
            return socialAccountPort.register(new SocialAccountPort.SocialAccountRegistration(
                    identity.provider(),
                    identity.providerUserId(),
                    identity.email(),
                    identity.name()
            ));
        } catch (DuplicateEmailException |
                 SocialAccountPort.SocialIdentityRegistrationConflictException registrationConflict) {
            return socialAccountPort.findUser(identity.provider(), identity.providerUserId())
                    .orElseThrow(() -> registrationConflict);
        }
    }

    private void validateRegistrationIdentity(OAuthIdentity identity) {
        if (!identity.emailVerified()
                || isBlank(identity.email())
                || identity.email().length() > 254
                || !EMAIL_PATTERN.matcher(identity.email().trim()).matches()
                || isBlank(identity.name())
                || identity.name().length() > 100) {
            throw new InvalidOAuthTokenException();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void registerRefreshSession(AuthTokenIssuer.IssuedAuthTokens tokens, Long userId) {
        try {
            refreshSessionStore.register(tokens.refreshToken(), userId, tokens.refreshTokenTtl());
        } catch (RuntimeException registrationFailure) {
            compensateRefreshSession(tokens.refreshToken(), userId, registrationFailure);
            throw registrationFailure;
        }
    }

    private void compensateRefreshSession(String refreshToken, Long userId, RuntimeException registrationFailure) {
        try {
            refreshSessionStore.revoke(refreshToken, userId);
        } catch (RuntimeException compensationFailure) {
            registrationFailure.addSuppressed(compensationFailure);
        }
    }
}
