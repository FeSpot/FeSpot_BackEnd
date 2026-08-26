package com.api.fespot.domain.auth.repository.jpa;

import com.api.fespot.domain.auth.entity.SocialCredential;
import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.auth.exception.DuplicateEmailException;
import com.api.fespot.domain.auth.repository.SocialAccountPort;
import com.api.fespot.domain.user.EmailNormalizer;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
public class JpaSocialAccountAdapter implements SocialAccountPort {

    private final UserRepository userRepository;
    private final SocialCredentialRepository credentialRepository;

    @Override
    public Optional<User> findUser(OAuthProvider provider, String providerUserId) {
        return credentialRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .map(SocialCredential::getUser);
    }

    @Override
    public boolean existsUserByEmail(String email) {
        return userRepository.existsByEmail(EmailNormalizer.normalize(email));
    }

    @Override
    @Transactional
    public User register(SocialAccountRegistration registration) {
        String normalizedEmail = EmailNormalizer.normalize(registration.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }

        try {
            User savedUser = userRepository.saveAndFlush(User.create(normalizedEmail, registration.name()));
            credentialRepository.saveAndFlush(SocialCredential.create(
                    savedUser,
                    registration.provider(),
                    registration.providerUserId()
            ));
            return savedUser;
        } catch (DataIntegrityViolationException exception) {
            if (isEmailUniqueConstraintViolation(exception)) {
                throw new DuplicateEmailException();
            }
            if (isSocialIdentityUniqueConstraintViolation(exception)) {
                throw new SocialIdentityRegistrationConflictException();
            }
            throw exception;
        }
    }

    @Override
    @Transactional
    public void storeProviderRefreshToken(OAuthProvider provider, String providerUserId, String refreshToken) {
        credentialRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .ifPresent(credential -> credential.updateProviderRefreshToken(refreshToken));
    }

    @Override
    public boolean hasProviderRefreshToken(OAuthProvider provider, String providerUserId) {
        return credentialRepository
                .existsByProviderAndProviderUserIdAndProviderRefreshTokenIsNotNull(provider, providerUserId);
    }

    private boolean isSocialIdentityUniqueConstraintViolation(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null
                    && message.toLowerCase().contains("uk_social_credentials_provider_user_id")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isEmailUniqueConstraintViolation(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase().contains("uk_users_email")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
