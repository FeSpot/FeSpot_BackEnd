package com.api.fespot.domain.report.entity;

import com.api.fespot.domain.user.entity.User;
import com.api.fespot.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "report_likes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "report_like_unique",
                        columnNames = {"user_id", "report_id"}
                )
        }

)
public class ReportLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    private ReportLike(User user, Report report) {
        this.user = user;
        this.report = report;
    }

    public static ReportLike create(User user, Report report) {
        return new ReportLike(user, report);
    }
}
