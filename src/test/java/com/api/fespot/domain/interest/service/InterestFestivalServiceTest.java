package com.api.fespot.domain.interest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalSummaryReadModel;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.festival.repository.FestivalRepository;
import com.api.fespot.domain.interest.entity.InterestFestival;
import com.api.fespot.domain.interest.enums.InterestFestivalErrorResponseCode;
import com.api.fespot.domain.interest.repository.InterestFestivalRepository;
import com.api.fespot.domain.interest.web.dto.InterestFestivalInfo;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.response.CursorPage;
import com.api.fespot.global.response.code.ErrorResponseCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class InterestFestivalServiceTest {

    private static final Long USER_ID = 1L;
    private static final String CONTENT_ID = "100";
    private static final int YEAR = 2026;

    private final InterestFestivalRepository interestFestivalRepository =
            mock(InterestFestivalRepository.class);
    private final FestivalRepository festivalRepository = mock(FestivalRepository.class);
    private final FestivalJpaRepository festivalJpaRepository =
            mock(FestivalJpaRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final InterestFestivalService interestFestivalService = new InterestFestivalService(
            interestFestivalRepository,
            festivalRepository,
            festivalJpaRepository,
            userRepository);

    @Test
    void savesInterestFestivalAndReturnsId() {
        User user = user(USER_ID);
        Festival festival = festival();
        InterestFestival saved = interest(10L, user, festival);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(interestFestivalRepository.existsByUserAndFestival(user, festival))
                .willReturn(false);
        given(interestFestivalRepository.save(any(InterestFestival.class)))
                .willReturn(saved);

        Long savedId = interestFestivalService.saveInterestFestival(USER_ID, YEAR, CONTENT_ID);

        assertThat(savedId).isEqualTo(10L);
    }

    @Test
    void rejectsSaveWhenTokenUserNoLongerExists() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                interestFestivalService.saveInterestFestival(USER_ID, YEAR, CONTENT_ID))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(ErrorResponseCode.UNAUTHORIZED));

        verifyNoInteractions(festivalJpaRepository, interestFestivalRepository);
    }

    @Test
    void rejectsSaveWhenFestivalIsNotRegistered() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                interestFestivalService.saveInterestFestival(USER_ID, YEAR, CONTENT_ID))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));

        verifyNoInteractions(interestFestivalRepository);
    }

    @Test
    void rejectsSaveWhenInterestAlreadyExists() {
        User user = user(USER_ID);
        Festival festival = festival();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(interestFestivalRepository.existsByUserAndFestival(user, festival))
                .willReturn(true);

        assertThatThrownBy(() ->
                interestFestivalService.saveInterestFestival(USER_ID, YEAR, CONTENT_ID))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(InterestFestivalErrorResponseCode
                                        .INTEREST_FESTIVAL_ALREADY_EXISTS));

        verify(interestFestivalRepository, never()).save(any(InterestFestival.class));
    }

    @Test
    void rejectsSaveWhenConcurrentRequestViolatesUniqueConstraint() {
        User user = user(USER_ID);
        Festival festival = festival();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(festivalJpaRepository.findByContentIdAndYear(CONTENT_ID, YEAR))
                .willReturn(Optional.of(festival));
        given(interestFestivalRepository.existsByUserAndFestival(user, festival))
                .willReturn(false);
        given(interestFestivalRepository.save(any(InterestFestival.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() ->
                interestFestivalService.saveInterestFestival(USER_ID, YEAR, CONTENT_ID))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsDeleteWhenInterestDoesNotExist() {
        given(interestFestivalRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                interestFestivalService.deleteInterestFestival(USER_ID, 10L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(InterestFestivalErrorResponseCode
                                        .INTEREST_FESTIVAL_NOT_FOUND));
    }

    @Test
    void rejectsDeleteWhenRequesterIsNotOwner() {
        InterestFestival interest = interest(10L, user(2L), festival());
        given(interestFestivalRepository.findById(10L)).willReturn(Optional.of(interest));

        assertThatThrownBy(() ->
                interestFestivalService.deleteInterestFestival(USER_ID, 10L))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getBaseResponseCode())
                                .isEqualTo(InterestFestivalErrorResponseCode
                                        .INTEREST_FESTIVAL_FORBIDDEN));

        verify(interestFestivalRepository, never()).deleteById(10L);
    }

    @Test
    void deletesInterestWhenRequesterIsOwner() {
        InterestFestival interest = interest(10L, user(USER_ID), festival());
        given(interestFestivalRepository.findById(10L)).willReturn(Optional.of(interest));

        interestFestivalService.deleteInterestFestival(USER_ID, 10L);

        verify(interestFestivalRepository).deleteById(10L);
    }

    @Test
    void readsFirstPageWhenCursorIsNull() {
        PageRequest pageRequest = PageRequest.of(0, 3);
        given(interestFestivalRepository.findFirstPageByUserId(USER_ID, YEAR, pageRequest))
                .willReturn(List.of());

        interestFestivalService.findPagedInterestFestival(USER_ID, YEAR, null, 2);

        verify(interestFestivalRepository).findFirstPageByUserId(USER_ID, YEAR, pageRequest);
        verify(interestFestivalRepository, never())
                .findPagedByUserId(anyLong(), anyInt(), anyLong(), any(PageRequest.class));
    }

    @Test
    void readsNextPageWhenCursorIsGiven() {
        PageRequest pageRequest = PageRequest.of(0, 3);
        given(interestFestivalRepository.findPagedByUserId(USER_ID, YEAR, 20L, pageRequest))
                .willReturn(List.of());

        interestFestivalService.findPagedInterestFestival(USER_ID, YEAR, 20L, 2);

        verify(interestFestivalRepository).findPagedByUserId(USER_ID, YEAR, 20L, pageRequest);
        verify(interestFestivalRepository, never())
                .findFirstPageByUserId(anyLong(), anyInt(), any(PageRequest.class));
    }

    @Test
    void returnsEmptyCursorPageWhenNoInterestExists() {
        given(interestFestivalRepository.findFirstPageByUserId(USER_ID, YEAR, PageRequest.of(0, 3)))
                .willReturn(List.of());

        CursorPage<InterestFestivalInfo> response =
                interestFestivalService.findPagedInterestFestival(USER_ID, YEAR, null, 2);

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getPageInfo().hasNext()).isFalse();
        assertThat(response.getPageInfo().nextCursor()).isNull();
        verifyNoInteractions(festivalRepository);
    }

    @Test
    void trimsExtraRowAndExposesNextCursorWhenNextPageExists() {
        User user = user(USER_ID);
        List<InterestFestival> paged = List.of(
                interest(30L, user, festival("300")),
                interest(20L, user, festival("200")),
                interest(10L, user, festival("100")));
        given(interestFestivalRepository.findFirstPageByUserId(USER_ID, YEAR, PageRequest.of(0, 3)))
                .willReturn(paged);
        given(festivalRepository.findByYear(YEAR)).willReturn(List.of(
                summary("100", "축제100"),
                summary("200", "축제200"),
                summary("300", "축제300")));

        CursorPage<InterestFestivalInfo> response =
                interestFestivalService.findPagedInterestFestival(USER_ID, YEAR, null, 2);

        assertThat(response.getPageInfo().hasNext()).isTrue();
        assertThat(response.getPageInfo().nextCursor()).isEqualTo("20");
        assertThat(response.getItems())
                .extracting(InterestFestivalInfo::interestFestivalId)
                .containsExactly(30L, 20L);
    }

    @Test
    void keepsCursorOrderAndFormatsDuration() {
        User user = user(USER_ID);
        List<InterestFestival> paged = List.of(
                interest(20L, user, festival("200")),
                interest(10L, user, festival("100")));
        given(interestFestivalRepository.findFirstPageByUserId(USER_ID, YEAR, PageRequest.of(0, 3)))
                .willReturn(paged);
        given(festivalRepository.findByYear(YEAR)).willReturn(List.of(
                summary("100", "축제100"),
                summary("200", "축제200")));

        CursorPage<InterestFestivalInfo> response =
                interestFestivalService.findPagedInterestFestival(USER_ID, YEAR, null, 2);

        assertThat(response.getPageInfo().hasNext()).isFalse();
        assertThat(response.getPageInfo().nextCursor()).isNull();
        assertThat(response.getItems())
                .extracting(InterestFestivalInfo::festivalName)
                .containsExactly("축제200", "축제100");
        assertThat(response.getItems().getFirst().festivalDuration())
                .isEqualTo("08.01 - 08.03");
    }

    @Test
    void filtersInterestWhoseFestivalIsMissingFromOpenApi() {
        User user = user(USER_ID);
        List<InterestFestival> paged = List.of(
                interest(20L, user, festival("200")),
                interest(10L, user, festival("100")));
        given(interestFestivalRepository.findFirstPageByUserId(USER_ID, YEAR, PageRequest.of(0, 3)))
                .willReturn(paged);
        given(festivalRepository.findByYear(YEAR)).willReturn(List.of(
                summary("100", "축제100")));

        CursorPage<InterestFestivalInfo> response =
                interestFestivalService.findPagedInterestFestival(USER_ID, YEAR, null, 2);

        assertThat(response.getItems())
                .extracting(InterestFestivalInfo::contentId)
                .containsExactly("100");
    }

    private User user(Long id) {
        User user = User.create("user@example.com", "tester");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Festival festival() {
        return festival(CONTENT_ID);
    }

    private Festival festival(String contentId) {
        return Festival.create(contentId, YEAR);
    }

    private InterestFestival interest(Long id, User user, Festival festival) {
        InterestFestival interest = InterestFestival.builder()
                .user(user)
                .festival(festival)
                .build();
        ReflectionTestUtils.setField(interest, "id", id);
        return interest;
    }

    private FestivalSummaryReadModel summary(String contentId, String name) {
        return new FestivalSummaryReadModel(
                contentId,
                name,
                "부산광역시",
                LocalDate.of(YEAR, 8, 1),
                LocalDate.of(YEAR, 8, 3),
                null,
                null,
                "한국관광공사 OpenAPI",
                Instant.now());
    }
}
