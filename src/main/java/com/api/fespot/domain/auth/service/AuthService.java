package com.api.fespot.domain.auth.service;

import com.api.fespot.domain.auth.entity.LocalCredential;
import com.api.fespot.domain.auth.exception.AuthErrorResponseCode;
import com.api.fespot.domain.auth.repository.RefreshSessionStore;
import com.api.fespot.domain.auth.repository.jpa.LocalCredentialRepository;
import com.api.fespot.domain.auth.service.port.AuthTokenIssuer;
import com.api.fespot.domain.auth.service.port.AuthTokenIssuer.IssuedAuthTokens;
import com.api.fespot.domain.auth.web.dto.LoginReq;
import com.api.fespot.domain.auth.web.dto.LoginRes;
import com.api.fespot.domain.auth.web.dto.SignUpReq;
import com.api.fespot.domain.auth.web.dto.SignUpRes;
import com.api.fespot.domain.user.EmailNormalizer;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_NAME_LENGTH = 100;

    private final UserRepository userRepository;
    private final LocalCredentialRepository localCredentialRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthTokenIssuer authTokenIssuer;
    private final RefreshSessionStore refreshSessionStore;

    @Transactional
    public SignUpRes signUp(SignUpReq request) {
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(AuthErrorResponseCode.DUPLICATED_EMAIL_409);
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = User.create(email, defaultName(email));

        try {
            User savedUser = userRepository.saveAndFlush(user);
            localCredentialRepository.saveAndFlush(LocalCredential.create(savedUser, passwordHash));
            return SignUpRes.from(savedUser);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(AuthErrorResponseCode.DUPLICATED_EMAIL_409);
        }
    }

    @Transactional(readOnly = true)
    public LoginRes login(LoginReq request) {
        String email = EmailNormalizer.normalize(request.email());
        LocalCredential credential = localCredentialRepository.findByUserEmail(email)
                .orElseThrow(this::loginFailed);

        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            throw loginFailed();
        }

        User user = credential.getUser();
        IssuedAuthTokens tokens = authTokenIssuer.issue(user);
        refreshSessionStore.register(tokens.refreshToken(), user.getId(), tokens.refreshTokenTtl());
        return LoginRes.bearer(tokens.accessToken(), tokens.refreshToken());
    }

    private CustomException loginFailed() {
        return new CustomException(AuthErrorResponseCode.LOGIN_FAILED_401);
    }

    private String defaultName(String email) {
        int separator = email.indexOf('@');
        String name = separator > 0 ? email.substring(0, separator) : email;
        // users.name 컬럼 길이(100) 초과 시 잘라 저장 — 초과분이 무결성 오류로 새면 이메일 중복 409로 오인된다.
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }
}
