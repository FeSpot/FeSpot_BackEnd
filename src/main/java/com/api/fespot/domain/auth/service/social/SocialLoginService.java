package com.api.fespot.domain.auth.service.social;

import com.api.fespot.domain.auth.exception.DuplicateEmailException;
import com.api.fespot.domain.auth.exception.InvalidOAuthTokenException;
import com.api.fespot.domain.auth.repository.RefreshSessionStore;
import com.api.fespot.domain.auth.repository.SocialAccountPort;
import com.api.fespot.domain.auth.service.port.AuthTokenIssuer;
import com.api.fespot.domain.user.entity.User;
import lombok.RequiredArgsConstructor;

import java.util.regex.Pattern;

@RequiredArgsConstructor
public class SocialLoginService implements SocialLoginUseCase {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
    );

    private final OAuthClientResolver clientResolver;
    private final SocialAccountPort socialAccountPort;
    private final AuthTokenIssuer tokenIssuer;
    private final RefreshSessionStore refreshSessionStore;

    @Override
    public SocialLoginResult login(SocialLoginCommand command) {
        OAuthClient client = clientResolver.resolve(command.provider());
        OAuthIdentity identity = command.expectedNonce() == null
                ? client.authenticate(command.credential())
                : client.authenticate(command.credential(), command.expectedNonce());
        validateLookupIdentity(command, identity);

        User user = socialAccountPort.findUser(identity.provider(), identity.providerUserId())
                .orElseGet(() -> register(identity));
        AuthTokenIssuer.IssuedAuthTokens tokens = tokenIssuer.issue(user);
        registerRefreshSession(tokens, user.getId());
        return new SocialLoginResult(tokens.accessToken(), tokens.refreshToken());
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
