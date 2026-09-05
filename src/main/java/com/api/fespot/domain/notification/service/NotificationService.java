package com.api.fespot.domain.notification.service;

import com.api.fespot.domain.notification.entity.PushToken;
import com.api.fespot.domain.notification.repository.PushTokenRepository;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.response.code.ErrorResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final UserRepository userRepository;
    private final PushTokenRepository pushTokenRepository;


    @Transactional
    public void register(Long userId, String token) {
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorResponseCode.UNAUTHORIZED));

        pushTokenRepository.findByToken(token)
                .ifPresentOrElse(
                        pushToken -> pushToken.registerTo(user),
                        () -> pushTokenRepository.save(PushToken.create(user, token))
                );
    }

    @Transactional
    public void unregister(Long userId, String token) {
        pushTokenRepository.deleteByUser_IdAndToken(userId, token);
    }

    @Transactional(readOnly = true)
    public List<String> findTokens(Long userId) {
        return pushTokenRepository.findTokensByUserId(userId);
    }
}
