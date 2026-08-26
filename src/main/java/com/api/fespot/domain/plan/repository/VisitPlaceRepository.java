package com.api.fespot.domain.plan.repository;

import com.api.fespot.domain.plan.entity.VisitPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitPlaceRepository extends JpaRepository<VisitPlace,Long> {

    // 회원 탈퇴 시 계획보다 먼저 일괄 삭제한다. 벌크 삭제는 orphanRemoval을 타지 않으므로 명시적으로 지운다.
    @Modifying
    @Query("""
delete from VisitPlace place
where place.visitPlan.id in (
    select plan.id
    from VisitPlan plan
    where plan.user.id = :userId
)
""")
    void deleteAllByUserId(@Param("userId") Long userId);
}
