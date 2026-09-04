package com.api.fespot.domain.user.service;

import com.api.fespot.domain.auth.repository.jpa.CredentialRepository;
import com.api.fespot.domain.festival.repository.FestivalLikeRepository;
import com.api.fespot.domain.festival.repository.FestivalNotificationRepository;
import com.api.fespot.domain.notification.repository.PushDeliveryTicketRepository;
import com.api.fespot.domain.notification.repository.PushTokenRepository;
import com.api.fespot.domain.plan.repository.VisitPlaceRepository;
import com.api.fespot.domain.plan.repository.VisitPlanRepository;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.exception.UserNotFoundException;
import com.api.fespot.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 시 사용자와 연관 데이터 전체를 한 트랜잭션에서 삭제한다.
 * FK 방향에 맞춰 자식 테이블부터 지운다: 일정 → 계획 → 찜/알림 → 인증 정보 → 사용자.
 */
@Service
@RequiredArgsConstructor
public class UserAccountEraser {

    private final UserRepository userRepository;
    private final VisitPlaceRepository visitPlaceRepository;
    private final VisitPlanRepository visitPlanRepository;
    private final FestivalLikeRepository festivalLikeRepository;
    private final FestivalNotificationRepository festivalNotificationRepository;
    private final CredentialRepository credentialRepository;
    private final PushTokenRepository pushTokenRepository;
    private final PushDeliveryTicketRepository pushDeliveryTicketRepository;

    @Transactional
    public void erase(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        visitPlaceRepository.deleteAllByUserId(userId);
        visitPlanRepository.deleteAllByUserId(userId);
        festivalLikeRepository.deleteAllByUserId(userId);
        festivalNotificationRepository.deleteAllByUserId(userId);
        pushDeliveryTicketRepository.deleteAllByUserId(userId);
        pushTokenRepository.deleteAllByUserId(userId);
        credentialRepository.findByUser_Id(userId).ifPresent(credentialRepository::delete);
        userRepository.delete(user);
    }
}
