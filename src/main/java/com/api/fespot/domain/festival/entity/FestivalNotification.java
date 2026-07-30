package com.api.fespot.domain.festival.entity;

import com.api.fespot.domain.user.entity.User;
import com.api.fespot.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "festival_notifications",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_festival_notification_user_festival",
                        columnNames = {"user_id", "festival_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FestivalNotification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "festival_notification_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_id", nullable = false)
    private Festival festival;

    private FestivalNotification(User user, Festival festival) {
        this.user = user;
        this.festival = festival;
    }

    public static FestivalNotification create(User user, Festival festival) {
        return new FestivalNotification(user, festival);
    }
}
