package com.api.fespot.domain.plan.repository;

import com.api.fespot.domain.plan.entity.VisitPlan;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VisitPlanRepository extends JpaRepository<VisitPlan,Long> {

    // userId, planId로 클라이언트가 생성한 계획에 대해 조회하는 메소드
    @Query("""
select plan
from VisitPlan plan
    left join fetch plan.places as places
where plan.user.id = :userId
    and plan.id = :planId
""")
    Optional<VisitPlan> findVisitPlanWithVisitPlaceByUser_IdAndId(
            @Param("userId") Long userId,
            @Param("planId") Long planId
    );

    // 커서 페이징 1단계: 컬렉션 fetch join과 limit을 함께 쓰면 인메모리 페이징이 되므로 id만 먼저 조회한다.
    @Query("""
select plan.id
from VisitPlan plan
where plan.user.id = :userId
    and (:cursor is null or plan.id < :cursor)
order by plan.id desc
""")
    List<Long> findIdsByUser_IdAndCursor(
            @Param("userId") Long userId,
            @Param("cursor") Long cursor,
            Limit limit
    );

    // 커서 페이징 2단계: 선별된 id들에 대해서만 방문 장소를 fetch join으로 로딩한다.
    @Query("""
select plan
from VisitPlan plan
    left join fetch plan.places as places
where plan.id in :planIds
order by plan.id desc
""")
    List<VisitPlan> findAllWithVisitPlaceByIdIn(
            @Param("planIds") List<Long> planIds
    );

    long countByUser_Id(Long userId);

    @Query("""
select plan.id
from VisitPlan plan
where plan.planTargetDate = :today
    and (:cursor is null or plan.id < :cursor)
    and (plan.morningNotificationAttemptedDate is null
        or plan.morningNotificationAttemptedDate <> :today)
    and exists (
        select notification.id
        from FestivalNotification notification
        where notification.user = plan.user
            and notification.festival = plan.festival
    )
    and exists (
        select pushToken.id
        from PushToken pushToken
        where pushToken.user = plan.user
    )
order by plan.id desc
""")
    List<Long> findMorningNotificationTargets(
            @Param("today") LocalDate today,
            @Param("cursor") Long cursor,
            Limit limit
    );

    @Query("""
select pushToken.token
from VisitPlan plan, FestivalNotification notification, PushToken pushToken
where plan.id = :planId
    and plan.planTargetDate = :today
    and (plan.morningNotificationAttemptedDate is null
        or plan.morningNotificationAttemptedDate <> :today)
    and notification.user = plan.user
    and notification.festival = plan.festival
    and pushToken.user = plan.user
""")
    Optional<String> findMorningNotificationPushToken(
            @Param("planId") Long planId,
            @Param("today") LocalDate today
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
update VisitPlan plan
set plan.morningNotificationAttemptedDate = :today
where plan.id = :planId
    and plan.planTargetDate = :today
    and (plan.morningNotificationAttemptedDate is null
        or plan.morningNotificationAttemptedDate <> :today)
    and exists (
        select notification.id
        from FestivalNotification notification
        where notification.user = plan.user
            and notification.festival = plan.festival
    )
    and exists (
        select pushToken.id
        from PushToken pushToken
        where pushToken.user = plan.user
            and pushToken.token = :pushToken
    )
""")
    int markMorningNotificationAttemptedIfEligible(
            @Param("planId") Long planId,
            @Param("today") LocalDate today,
            @Param("pushToken") String pushToken
    );

    // 회원 탈퇴 시 사용자의 모든 계획을 일괄 삭제한다.
    @Modifying
    @Query("""
delete from VisitPlan plan
where plan.user.id = :userId
""")
    void deleteAllByUserId(@Param("userId") Long userId);
}
