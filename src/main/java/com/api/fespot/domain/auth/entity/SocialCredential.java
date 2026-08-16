package com.api.fespot.domain.auth.entity;

import com.api.fespot.domain.auth.enums.OAuthProvider;
import com.api.fespot.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(
        name = "social_credentials",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_social_credentials_provider_user_id",
                columnNames = {"provider", "provider_user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialCredential extends Credential {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Builder(access = AccessLevel.PRIVATE)
    private SocialCredential(User user, OAuthProvider provider, String providerUserId) {
        super(user);
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    public static SocialCredential create(User user, OAuthProvider provider, String providerUserId) {
        return SocialCredential.builder()
                .user(user)
                .provider(provider)
                .providerUserId(providerUserId)
                .build();
    }
}
