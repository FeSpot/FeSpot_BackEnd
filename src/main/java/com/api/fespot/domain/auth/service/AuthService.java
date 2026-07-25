package com.api.fespot.domain.auth.service;

import com.api.fespot.domain.auth.exception.AuthErrorResponseCode;
import com.api.fespot.domain.auth.web.dto.LoginReq;
import com.api.fespot.domain.auth.web.dto.LoginRes;
import com.api.fespot.domain.auth.web.dto.SignUpReq;
import com.api.fespot.domain.auth.web.dto.SignUpRes;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.security.AuthPrincipal;
import com.api.fespot.global.security.jwt.JwtTokenProvider;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public SignUpRes signUp(SignUpReq request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(AuthErrorResponseCode.DUPLICATED_EMAIL_409);
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = User.create(email, passwordHash);

        try {
            User savedUser = userRepository.saveAndFlush(user);
            return SignUpRes.from(savedUser);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(AuthErrorResponseCode.DUPLICATED_EMAIL_409);
        }
    }

    @Transactional(readOnly = true)
    public LoginRes login(LoginReq request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(this::loginFailed);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw loginFailed();
        }

        Authentication authentication = createAuthentication(user);
        String accessToken = jwtTokenProvider.createAccessToken(authentication);
        return new LoginRes("Bearer", accessToken);
    }

    private Authentication createAuthentication(User user) {
        AuthPrincipal principal = AuthPrincipal.of(
                user.getId(),
                user.getEmail(),
                List.of(user.getRole().name()));
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
    }

    private CustomException loginFailed() {
        return new CustomException(AuthErrorResponseCode.LOGIN_FAILED_401);
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }
}
