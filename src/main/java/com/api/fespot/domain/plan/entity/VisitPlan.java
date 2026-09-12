package com.api.fespot.domain.plan.entity;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.user.entity.User;
import com.api.fespot.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        uniqueConstraints = @UniqueConstraint(columnNames = {
                "user_id", "festival_id", "plan_target_date"
        })
)
public class VisitPlan extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private LocalDate planTargetDate;

    // Expo 전달 성공일이 아니라, 중복 발송을 막기 위해 호출 전에 기록하는 시도일이다.
    @Column(name = "morning_notification_attempted_date")
    private LocalDate morningNotificationAttemptedDate;

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

    public void modifyTargetDate(LocalDate planTargetDate) {
        this.planTargetDate = planTargetDate;
    }
}
