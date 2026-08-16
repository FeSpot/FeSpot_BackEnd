package com.api.fespot.domain.plan.entity;

import com.api.fespot.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitPlace extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10, unique = true)
    private LocalTime startAt;

    @Column(nullable = false, length = 20)
    private String placeName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_plan_id", nullable = false)
    private VisitPlan visitPlan;

    @Builder
    private VisitPlace(LocalTime startAt, String placeName, VisitPlan visitPlan) {
        this.startAt = startAt;
        this.placeName = placeName;
        this.visitPlan = visitPlan;
        visitPlan.getPlaces().add(this);
    }

    public void modify(LocalTime startAt, String placeName) {
        this.startAt = startAt;
        this.placeName = placeName;
    }
}
