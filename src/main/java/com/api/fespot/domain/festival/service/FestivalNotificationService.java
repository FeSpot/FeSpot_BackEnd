package com.api.fespot.domain.festival.service;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalNotification;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.festival.repository.FestivalNotificationRepository;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.response.code.ErrorResponseCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FestivalNotificationService {

    private final UserRepository userRepository;
    private final FestivalJpaRepository festivalJpaRepository;
    private final FestivalNotificationRepository festivalNotificationRepository;

    @Transactional
    public void updateNotification(
            Long userId,
            String contentId,
            int year,
            boolean enabled
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(
                        ErrorResponseCode.UNAUTHORIZED));

        Festival festival = festivalJpaRepository
                .findByContentIdAndYear(contentId, year)
                .orElseThrow(() -> new CustomException(
                        FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));

        Optional<FestivalNotification> existingNotification =
                festivalNotificationRepository.findByUserAndFestival(
                        user,
                        festival);

        if (enabled) {
            if (existingNotification.isEmpty()) {
                festivalNotificationRepository.save(
                        FestivalNotification.create(user, festival));
            }
        } else {
            existingNotification.ifPresent(
                    festivalNotificationRepository::delete);
        }
    }
}
