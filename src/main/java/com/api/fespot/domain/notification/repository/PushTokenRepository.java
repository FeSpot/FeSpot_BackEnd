package com.api.fespot.domain.notification.repository;

import com.api.fespot.domain.notification.entity.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {
    Optional<PushToken> findByToken(String token);

    @Query("""
            select pushToken.token
            from PushToken pushToken
            where pushToken.user.id = :userId
            """)
    List<String> findTokensByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("""
            delete from PushToken pushToken
            where pushToken.user.id = :userId
            """)
    void deleteAllByUserId(@Param("userId") Long userId);
}
