package com.api.fespot.domain.plan.service;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.exception.FestivalErrorResponseCode;
import com.api.fespot.domain.festival.repository.FestivalJpaRepository;
import com.api.fespot.domain.user.exception.UserErrorResponseCode;
import com.api.fespot.global.exception.CustomException;
import com.api.fespot.domain.plan.entity.VisitPlace;
import com.api.fespot.domain.plan.entity.VisitPlan;
import com.api.fespot.domain.plan.repository.VisitPlaceRepository;
import com.api.fespot.domain.plan.repository.VisitPlanRepository;
import com.api.fespot.domain.plan.web.dto.PlanRequest;
import com.api.fespot.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    private final FestivalJpaRepository festivalJpaRepository;
    private final UserRepository userRepository;

    private final VisitPlanRepository visitPlanRepository;
    private final VisitPlaceRepository visitPlaceRepository;

    @Transactional
    public VisitPlan createVisitPlan(Long userId, PlanRequest request) {
        // festivalKey가 가리키는 festival을 영속성 컨텍스트에 로딩
        Festival festival = festivalJpaRepository.findByContentIdAndYear(request.contentId(), request.startDate().getYear())
                .orElseThrow(() -> new CustomException(FestivalErrorResponseCode.FESTIVAL_NOT_FOUND_404));

        VisitPlan visitPlan = VisitPlan.builder()
                .planTargetDate(request.targetDate())
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

    public VisitPlan findDetailVisitPlan(Long userId, Long planId) {
        return visitPlanRepository.findVisitPlanWithVisitPlaceByUser_IdAndId(userId, planId)
                .orElseThrow(RuntimeException::new);
    }

    @Transactional
    public VisitPlan updateVisitPlan(Long userId, Long visitPlanId, PlanRequest request) {

        Map<Long, VisitPlace> foundPlaces = new HashMap<>();

        VisitPlan visitPlan = visitPlanRepository.findVisitPlanWithVisitPlaceByUser_IdAndId(userId, visitPlanId)
                .orElseThrow(RuntimeException::new);

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
                    updated.placeType() == null ? visitPlace.getPlaceType() : updated.placeType(),
                    updated.startedAt() == null ? visitPlace.getStartAt() : updated.startedAt(),
                    updated.name() == null ? visitPlace.getPlaceName() : updated.name());
        }
        // 식별자가 없는 것들을 저장
        places = request.plans().stream()
               .filter(plan -> plan.placeId() == null)
               .map(plan -> plan.convertToVisitPlace(visitPlan)).toList();
        return places;
    }
}
