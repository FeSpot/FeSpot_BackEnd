package com.api.fespot.domain.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.api.fespot.domain.auth.entity.LocalCredential;
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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class UserAccountEraserTest {

    private static final Long USER_ID = 1L;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final VisitPlaceRepository visitPlaceRepository = mock(VisitPlaceRepository.class);
    private final VisitPlanRepository visitPlanRepository = mock(VisitPlanRepository.class);
    private final FestivalLikeRepository festivalLikeRepository = mock(FestivalLikeRepository.class);
    private final FestivalNotificationRepository festivalNotificationRepository =
            mock(FestivalNotificationRepository.class);
    private final CredentialRepository credentialRepository = mock(CredentialRepository.class);
    private final PushTokenRepository pushTokenRepository = mock(PushTokenRepository.class);
    private final PushDeliveryTicketRepository pushDeliveryTicketRepository =
            mock(PushDeliveryTicketRepository.class);

    private final UserAccountEraser userAccountEraser = new UserAccountEraser(
            userRepository,
            visitPlaceRepository,
            visitPlanRepository,
            festivalLikeRepository,
            festivalNotificationRepository,
            credentialRepository,
            pushTokenRepository,
            pushDeliveryTicketRepository);

    @Test
    void erasesDependentRowsBeforeUser() {
        User user = User.create("user@example.com", "user");
        LocalCredential credential = LocalCredential.create(user, "password-hash");
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(credentialRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(credential));

        userAccountEraser.erase(USER_ID);

        InOrder inOrder = Mockito.inOrder(
                visitPlaceRepository, visitPlanRepository, festivalLikeRepository,
                festivalNotificationRepository, pushDeliveryTicketRepository, pushTokenRepository,
                credentialRepository, userRepository);
        inOrder.verify(userRepository).findByIdForUpdate(USER_ID);
        inOrder.verify(visitPlaceRepository).deleteAllByUserId(USER_ID);
        inOrder.verify(visitPlanRepository).deleteAllByUserId(USER_ID);
        inOrder.verify(festivalLikeRepository).deleteAllByUserId(USER_ID);
        inOrder.verify(festivalNotificationRepository).deleteAllByUserId(USER_ID);
        inOrder.verify(pushDeliveryTicketRepository).deleteAllByUserId(USER_ID);
        inOrder.verify(pushTokenRepository).deleteAllByUserId(USER_ID);
        inOrder.verify(credentialRepository).delete(credential);
        inOrder.verify(userRepository).delete(user);
    }

    @Test
    void erasesUserWithoutCredentialRow() {
        User user = User.create("user@example.com", "user");
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(credentialRepository.findByUser_Id(USER_ID)).willReturn(Optional.empty());

        userAccountEraser.erase(USER_ID);

        verify(credentialRepository, never()).delete(any());
        verify(userRepository).delete(user);
    }

    @Test
    void rejectsUnknownUser() {
        given(userRepository.findByIdForUpdate(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userAccountEraser.erase(USER_ID))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).delete(any(User.class));
        verify(visitPlanRepository, never()).deleteAllByUserId(USER_ID);
        verify(pushDeliveryTicketRepository, never()).deleteAllByUserId(USER_ID);
        verify(pushTokenRepository, never()).deleteAllByUserId(USER_ID);
    }
}
