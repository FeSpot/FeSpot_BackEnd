package com.api.fespot.domain.plan.entity;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitPlan extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private LocalDate planTargetDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "festival_id", nullable = false)
    private Festival festival;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "visitPlan", orphanRemoval = true)
    private List<VisitPlace> places = new ArrayList<>();

    @Builder
    private VisitPlan(LocalDate planTargetDate, User user, Festival festival) {
        this.planTargetDate = planTargetDate;
        this.user = user;
        this.festival = festival;
    }
}
