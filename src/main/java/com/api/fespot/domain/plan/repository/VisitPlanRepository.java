package com.api.fespot.domain.plan.repository;

import com.api.fespot.domain.plan.entity.VisitPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VisitPlanRepository extends JpaRepository<VisitPlan,Long> {

    // userId, planId로 클라이언트가 생성한 계획에 대해 조회하는 메소드
    @Query("""
select plan
from VisitPlan plan
    join plan.places as places
where plan.user.id = :userId
    and plan.id = :planId
""")
    Optional<VisitPlan> findVisitPlanWithVisitPlaceByUser_IdAndId(
            @Param("userId") Long userId,
            @Param("planId") Long planId
    );
}
