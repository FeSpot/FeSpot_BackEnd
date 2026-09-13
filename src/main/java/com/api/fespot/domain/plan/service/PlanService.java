package com.api.fespot.domain.plan.service;

import com.api.fespot.domain.festival.client.kto.KtoFestivalClient;
import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalSummaryReadModel;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.plan.entity.VisitPlace;
import com.api.fespot.domain.plan.entity.VisitPlan;
import com.api.fespot.domain.plan.enums.PlanErrorResponseCode;
import com.api.fespot.domain.plan.repository.VisitPlaceRepository;
import com.api.fespot.domain.plan.repository.VisitPlanRepository;
import com.api.fespot.domain.plan.service.dto.PlanListResult;
import com.api.fespot.domain.plan.web.dto.PlanRequest;
import com.api.fespot.domain.user.exception.UserErrorResponseCode;
import com.api.fespot.domain.user.repository.UserRepository;
import com.api.fespot.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    private final FestivalJpaRepository festivalJpaRepository;
    private final UserRepository userRepository;
    private final KtoFestivalClient ktoFestivalClient;

    private final VisitPlanRepository visitPlanRepository;
    private final VisitPlaceRepository visitPlaceRepository;

    @Transactional
    public VisitPlan createVisitPlan(Long userId, PlanRequest request) {
        // festivalKey가 가리키는 festival을 영속성 컨텍스트에 로딩
        Festival festival = festivalJpaRepository.findByContentIdAndYear(request.contentId(), request.startDate().getYear())
                .orElseThrow(() -> new CustomException(FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));

        VisitPlan visitPlan = VisitPlan.builder()
                .planTargetDate(request.targetDate())
                .title(request.planName())
                .user(userRepository.findById(userId)
                        .orElseThrow(() -> new CustomException(UserErrorResponseCode.USER_NOT_FOUND_404))
                )
                .festival(festival)
                .build();

        VisitPlan savedVisitPlan = visitPlanRepository.save(visitPlan);

        // List<VisitPlace> 빌드
        List<VisitPlace> list = request.plans().stream()
                .map(plan -> plan.convertToVisitPlace(savedVisitPlan))
                .toList();

        visitPlaceRepository.saveAll(list);

        return savedVisitPlan;
    }

    public PlanListResult findAllVisitPlans(Long userId, Long cursor, int size) {
        // limit + 1개를 조회해서 다음 페이지 존재 여부를 판단한다.
        List<Long> planIds = visitPlanRepository.findIdsByUser_IdAndCursor(userId, cursor, Limit.of(size + 1));
        Integer totalCount = Math.toIntExact(visitPlanRepository.countByUser_Id(userId));

        if (planIds.isEmpty()) {
            return PlanListResult.empty(totalCount);
        }

        boolean hasNext = planIds.size() > size;
        List<Long> pageIds = hasNext ? planIds.subList(0, size) : planIds;
        List<VisitPlan> plans = visitPlanRepository.findAllWithVisitPlaceByIdIn(pageIds);
        Long nextCursor = hasNext ? pageIds.get(pageIds.size() - 1) : null;

        // 방문 계획에 축제 제목을 바인딩
        List<FestivalSummaryReadModel> festivalOfCurrentYear = ktoFestivalClient.findByYear(LocalDate.now().getYear());

        Map<String, String> nameByContentId = festivalOfCurrentYear.stream()
                .collect(Collectors.toMap(
                        FestivalSummaryReadModel::contentId,
                        FestivalSummaryReadModel::name,
                        (first, second) -> first
                ));

        return new PlanListResult(
                totalCount,
                plans,
                nameByContentId,
                nextCursor,
                hasNext
        );
    }

    public VisitPlan findDetailVisitPlan(Long userId, Long planId) {
        return visitPlanRepository.findVisitPlanWithVisitPlaceByUser_IdAndId(userId, planId)
                .orElseThrow(() -> new CustomException(PlanErrorResponseCode.PLAN_NOT_FOUND));
    }

    @Transactional
    public VisitPlan updateVisitPlan(Long userId, Long visitPlanId, PlanRequest request) {

        Map<Long, VisitPlace> foundPlaces = new HashMap<>();

        VisitPlan visitPlan = visitPlanRepository.findVisitPlanWithVisitPlaceByUser_IdAndId(userId, visitPlanId)
                .orElseThrow(() -> new CustomException(PlanErrorResponseCode.PLAN_NOT_FOUND));

        visitPlan.modifyTargetDate(request.targetDate());

        // plans가 null이면 장소 수정 없음
        if (request.plans() == null) {
            return visitPlan;
        }

        for (VisitPlace i : visitPlan.getPlaces()) {
            foundPlaces.put(i.getId(), i);
        }

        List<VisitPlace> places = new ArrayList<>();

        for (PlanRequest.Plan updated :  request.plans()) {
            places = updateVisitPlaces(request, updated, foundPlaces, visitPlan);
        }

        visitPlaceRepository.saveAll(places);

        return visitPlan;
    }

    private @NonNull List<VisitPlace> updateVisitPlaces(
            PlanRequest request,
            PlanRequest.Plan updated,
            Map<Long, VisitPlace> foundPlaces,
            VisitPlan visitPlan
    ) {

        List<VisitPlace> places;

        // 식별자가 있는 것들을 수정
        if(updated.placeId() != null) {
            VisitPlace visitPlace = foundPlaces.get(updated.placeId());
            visitPlace.modify(
                    updated.startAt() == null ? visitPlace.getStartAt() : updated.startAt(),
                    updated.name() == null ? visitPlace.getPlaceName() : updated.name(),
                    updated.roadAddress() == null ? visitPlace.getRoadAddress() : updated.roadAddress()
            );
        }
        // 식별자가 없는 것들을 저장
        places = request.plans().stream()
               .filter(plan -> plan.placeId() == null)
               .map(plan -> plan.convertToVisitPlace(visitPlan)).toList();
        return places;
    }

    @Transactional
    public void deleteVisitPlan(Long userId, Long visitPlanId) {
        VisitPlan visitPlan = visitPlanRepository.findVisitPlanWithVisitPlaceByUser_IdAndId(userId, visitPlanId)
                .orElseThrow(() -> new CustomException(PlanErrorResponseCode.PLAN_NOT_FOUND));

        visitPlanRepository.delete(visitPlan);
    }
}
