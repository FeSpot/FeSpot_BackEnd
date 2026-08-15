package com.api.fespot.domain.auth.entity;

import com.api.fespot.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "local_credentials")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocalCredential extends Credential {

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    private LocalCredential(User user, String passwordHash) {
        super(user);
        this.passwordHash = passwordHash;
    }

    public static LocalCredential create(User user, String passwordHash) {
        return new LocalCredential(user, passwordHash);
    }
}
