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
        name = "festival_likes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_festival_like_user_festival",
                        columnNames = {"user_id", "festival_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FestivalLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "festival_like_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_id", nullable = false)
    private Festival festival;

    private FestivalLike(User user, Festival festival) {
        this.user = user;
        this.festival = festival;
    }

    public static FestivalLike create(User user, Festival festival) {
        return new FestivalLike(user, festival);
    }
}
