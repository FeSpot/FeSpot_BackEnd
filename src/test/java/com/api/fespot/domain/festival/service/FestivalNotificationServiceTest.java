package com.api.fespot.domain.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalNotification;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.festival.repository.FestivalNotificationRepository;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.exception.UserErrorResponseCode;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FestivalNotificationServiceTest {

    private static final Long USER_ID = 1L;
    private static final String CONTENT_ID = "100";
    private static final int YEAR = 2026;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final FestivalJpaRepository festivalJpaRepository =
            mock(FestivalJpaRepository.class);
    private final FestivalNotificationRepository festivalNotificationRepository =
            mock(FestivalNotificationRepository.class);
    private final FestivalNotificationService festivalNotificationService =
            new FestivalNotificationService(
                    userRepository,
                    festivalJpaRepository,
                    festivalNotificationRepository);

    @Test
    void createsNotificationWhenEnabledAndItDoesNotExist() {
        User user = user();
        Festival festival = festival();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(festivalNotificationRepository.findByUserAndFestival(
                user,
                festival))
                .willReturn(Optional.empty());

        festivalNotificationService.updateNotification(
                USER_ID,
                CONTENT_ID,
                YEAR,
                true);

        verify(festivalNotificationRepository)
                .save(any(FestivalNotification.class));
    }

    @Test
    void keepsNotificationEnabledWhenItAlreadyExists() {
        User user = user();
        Festival festival = festival();
        FestivalNotification notification =
                FestivalNotification.create(user, festival);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(festivalNotificationRepository.findByUserAndFestival(
                user,
                festival))
                .willReturn(Optional.of(notification));

        festivalNotificationService.updateNotification(
                USER_ID,
                CONTENT_ID,
                YEAR,
                true);

        verify(festivalNotificationRepository, never())
                .save(any(FestivalNotification.class));
        verify(festivalNotificationRepository, never())
                .delete(any(FestivalNotification.class));
    }

    @Test
    void deletesNotificationWhenDisabled() {
        User user = user();
        Festival festival = festival();
        FestivalNotification notification =
                FestivalNotification.create(user, festival);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(festivalNotificationRepository.findByUserAndFestival(
                user,
                festival))
                .willReturn(Optional.of(notification));

        festivalNotificationService.updateNotification(
                USER_ID,
                CONTENT_ID,
                YEAR,
                false);

        verify(festivalNotificationRepository).delete(notification);
    }

    @Test
    void keepsNotificationDisabledWhenItDoesNotExist() {
        User user = user();
        Festival festival = festival();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(festivalNotificationRepository.findByUserAndFestival(
                user,
                festival))
                .willReturn(Optional.empty());

        festivalNotificationService.updateNotification(
                USER_ID,
                CONTENT_ID,
                YEAR,
                false);

        verify(festivalNotificationRepository, never())
                .save(any(FestivalNotification.class));
        verify(festivalNotificationRepository, never())
                .delete(any(FestivalNotification.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsFestivalThatIsNotRegistered(boolean enabled) {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user()));
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                festivalNotificationService.updateNotification(
                        USER_ID,
                        CONTENT_ID,
                        YEAR,
                        enabled))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(
                                        FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));

        verifyNoInteractions(festivalNotificationRepository);
    }

    @Test
    void rejectsTokenUserThatNoLongerExists() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                festivalNotificationService.updateNotification(
                        USER_ID,
                        CONTENT_ID,
                        YEAR,
                        true))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(UserErrorResponseCode.USER_NOT_FOUND_404));

        verifyNoInteractions(
                festivalJpaRepository,
                festivalNotificationRepository);
    }

    private Festival festival() {
        return Festival.create(CONTENT_ID, YEAR);
    }

    private User user() {
        return User.create("user@example.com", "password-hash");
    }
}
