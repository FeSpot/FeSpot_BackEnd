package com.api.fespot.domain.plan.repository;

import com.api.fespot.domain.plan.entity.VisitPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitPlanRepository extends JpaRepository<VisitPlan,Long> {
}
