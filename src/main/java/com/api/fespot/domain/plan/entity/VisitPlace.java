package com.api.fespot.domain.plan.entity;

import com.api.fespot.domain.plan.entity.enums.PlaceType;
import com.api.fespot.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitPlace extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlaceType placeType;

    @Column(nullable = false, length = 10)
    private LocalTime startAt;

    @Column(nullable = false, length = 20)
    private String placeName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_plan_id", nullable = false)
    private VisitPlan visitPlan;

    @Builder
    private VisitPlace(PlaceType placeType, LocalTime startAt, String placeName, VisitPlan visitPlan) {
        this.placeType = placeType;
        this.startAt = startAt;
        this.placeName = placeName;
        this.visitPlan = visitPlan;
        visitPlan.getPlaces().add(this);
    }
}
