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

    @Column(nullable = false, length = 20)
    private String planName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "festival_id", nullable = false)
    private Festival festival;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "visitPlan", orphanRemoval = true)
    private List<VisitPlace> places = new ArrayList<>();

    @Builder
    private VisitPlan(LocalDate planTargetDate, String planName, User user, Festival festival) {
        this.planTargetDate = planTargetDate;
        this.planName = planName;
        this.user = user;
        this.festival = festival;
    }

    public void modifyTargetDate(LocalDate planTargetDate) {
        this.planTargetDate = planTargetDate;
    }
}
