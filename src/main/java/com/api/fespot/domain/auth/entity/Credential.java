package com.api.fespot.domain.auth.entity;

import com.api.fespot.domain.user.entity.User;
import com.api.fespot.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Inheritance(strategy = InheritanceType.JOINED)
@Table(
        name = "credentials",
        uniqueConstraints = @UniqueConstraint(name = "uk_credentials_user_id", columnNames = "user_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Credential extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    protected Credential(User user) {
        this.user = user;
    }
}
