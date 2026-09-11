package com.api.fespot.domain.notification.entity;

import com.api.fespot.domain.user.entity.User;
import com.api.fespot.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "push_tokens",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_push_tokens_expo_push_token",
                        columnNames = "expo_push_token"
                ),
                @UniqueConstraint(
                        name = "uk_push_tokens_user_id",
                        columnNames = "user_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "push_token_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(
            name = "expo_push_token",
            nullable = false,
            length = 512
    )
    private String token;

    private PushToken(User user, String token) {
        this.user = user;
        this.token = token;
    }

    public static PushToken create(User user, String token) {
        return new PushToken(user, token);
    }

    public void updateToken(String token){
        this.token = token;
    }
}