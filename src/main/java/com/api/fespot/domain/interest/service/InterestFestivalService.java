package com.api.fespot.domain.interest.service;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalSummaryReadModel;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.festival.repository.FestivalRepository;
import com.api.fespot.domain.interest.entity.InterestFestival;
import com.api.fespot.domain.interest.enums.InterestFestivalErrorResponseCode;
import com.api.fespot.domain.interest.exception.InterestFestivalAlreadyExistsException;
import com.api.fespot.domain.interest.repository.InterestFestivalRepository;
import com.api.fespot.domain.interest.web.dto.InterestFestivalInfo;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.aop.WrapUniqueViolation;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.global.response.CursorPage;
import com.api.fespot.global.response.code.ErrorResponseCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterestFestivalService {

    private final InterestFestivalRepository interestFestivalRepository;

    private final FestivalRepository festivalRepository;
    private final FestivalJpaRepository festivalJpaRepository;

    private final UserRepository userRepository;

    @WrapUniqueViolation(InterestFestivalAlreadyExistsException.class)
    @Transactional
    public Long saveInterestFestival(Long userId, int currentYear, String contentId) {

        User persistUser = persistUser(userId);

        // 1. 페스티벌 객체를 조회
        Festival festival = festivalJpaRepository.findByContentIdAndYear(contentId, currentYear)
                .orElseThrow(() -> new CustomException(FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));

        // 2. 이미 관심으로 등록한 축제를 다시 등록하려고 하는 경우 예외
        if (interestFestivalRepository.existsByUserAndFestival(persistUser, festival)) {
            throw new CustomException(InterestFestivalErrorResponseCode.INTEREST_FESTIVAL_ALREADY_EXISTS);
        }

        // 3. 관심 축제 엔터티 빌드
        InterestFestival interestFestival = InterestFestival.builder()
                .user(persistUser)
                .festival(festival)
                .build();

        // 4. 관심 축제로 저장
        InterestFestival saved = interestFestivalRepository.save(interestFestival);

        return saved.getId();
    }

    @Transactional
    public void deleteInterestFestival(Long userId, Long interestFestivalId) {

        // 리소스 존재 체크
        InterestFestival interest = interestFestivalRepository.findById(interestFestivalId)
                .orElseThrow(() -> new CustomException(InterestFestivalErrorResponseCode.INTEREST_FESTIVAL_NOT_FOUND));

        // 리소스 삭제 권한 체크
        if (!interest.getUser().getId().equals(userId)) {
            throw new CustomException(InterestFestivalErrorResponseCode.INTEREST_FESTIVAL_FORBIDDEN);
        }

        interestFestivalRepository.deleteById(interestFestivalId);
    }

    @Transactional(readOnly = true)
    public CursorPage<InterestFestivalInfo> findPagedInterestFestival(Long userId, int currentYear, Long cursorId, int size) {

        // 1. 1개 더 조회하여 다음 항목이 존재하는지 조회 (커서가 없으면 첫 페이지)
        PageRequest pageRequest = PageRequest.of(0, size + 1);
        List<InterestFestival> paged = cursorId == null
                ? interestFestivalRepository.findFirstPageByUserId(userId, currentYear, pageRequest)
                : interestFestivalRepository.findPagedByUserId(userId, currentYear, cursorId, pageRequest);

        if (paged.isEmpty()) {
            return CursorPage.empty();
        }

        return mappedCursorPagedResponse(currentYear, size, paged);
    }

    private CursorPage<InterestFestivalInfo> mappedCursorPagedResponse(int currentYear, int size, List<InterestFestival> paged) {

        boolean hasNext = paged.size() > size;

        // 1. 커서 페이징 아이템 빌드
        List<InterestFestival> interestFestivalList = hasNext ? paged.subList(0, size) : paged;

        // 2. 커서 페이징 정보 빌드 (커서는 잘라낸 후의 마지막 항목 기준, 마지막 페이지면 null)
        Long nextCursor = interestFestivalList.getLast().getId();
        CursorPage.PageInfo pageInfo = CursorPage.PageInfo.of(
                hasNext ? String.valueOf(nextCursor) : null, hasNext);


        // 2-1. 진행 축제 정보를 콘텐츠 아이디 기준 검색용 Map으로 변환
        Map<String, FestivalSummaryReadModel> festivalsByContentId = festivalRepository.findByYear(currentYear).stream()
                .collect(Collectors.toMap(
                        FestivalSummaryReadModel::contentId,
                        festival -> festival,
                        (first, duplicate) -> first
                ));

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("MM.dd");

        // 3. 실제 아이템 배열 빌드
        List<InterestFestivalInfo> items = interestFestivalList.stream()
                .filter(interest -> festivalsByContentId.containsKey(interest.getFestival().getContentId()))
                .map(interest -> {
                    FestivalSummaryReadModel festival = festivalsByContentId.get(interest.getFestival().getContentId());
                    return new InterestFestivalInfo(
                            interest.getId(),
                            festival.contentId(),
                            festival.name(),
                            festival.startDate().format(dateTimeFormatter) + " - " + festival.endDate().format(dateTimeFormatter)
                    );
                })
                .toList();

        return CursorPage.of(items, pageInfo);
    }

    private User persistUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorResponseCode.UNAUTHORIZED));
    }
}
