package com.api.fespot.domain.festival.service;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalLike;
import com.api.fespot.domain.festival.exception.DuplicateFestivalLikeException;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.festival.repository.FestivalLikeRepository;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.exception.UserErrorResponseCode;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.aop.WrapUniqueViolation;
import com.api.fespot.global.exception.CustomException;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FestivalLikeService {

    private final UserRepository userRepository;
    private final FestivalJpaRepository festivalJpaRepository;
    private final FestivalLikeRepository festivalLikeRepository;

    @Transactional
    @WrapUniqueViolation(
            value = DuplicateFestivalLikeException.class,
            constraint = "uk_festival_like_user_festival"
    )
    public void updateLike(
            Long userId,
            String contentId,
            int year,
            boolean liked
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND_404));

        Festival festival = festivalJpaRepository.findByContentIdAndYear(contentId, year)
                .orElseThrow(() -> new CustomException(FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));

        Optional<FestivalLike> existingLike = festivalLikeRepository.findByUserAndFestival(user, festival);

        if (liked) {
            if (existingLike.isEmpty()) {
                festivalLikeRepository.save(FestivalLike.create(user, festival));
            }
        } else {
            existingLike.ifPresent(festivalLikeRepository::delete);
        }
    }
}
