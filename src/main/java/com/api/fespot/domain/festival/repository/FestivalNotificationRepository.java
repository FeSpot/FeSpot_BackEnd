package com.api.fespot.domain.festival.repository;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalNotification;
import com.api.fespot.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FestivalNotificationRepository
        extends JpaRepository<FestivalNotification, Long> {

    Optional<FestivalNotification> findByUserAndFestival(
            User user,
            Festival festival
    );

    @Query("""
            select distinct notification.user.id
            from FestivalNotification notification, PushToken pushToken
            where notification.festival.id = :festivalId
              and pushToken.user = notification.user
            order by notification.user.id
            """)
    List<Long> findSubscriberIdsWithPushToken(
            @Param("festivalId") Long festivalId
    );

    @Query("""
            select pushToken.token
            from FestivalNotification notification, PushToken pushToken
            where notification.festival.id = :festivalId
              and notification.user.id in :userIds
              and pushToken.user = notification.user
            """)
    List<String> findCurrentPushTokens(
            @Param("festivalId") Long festivalId,
            @Param("userIds") List<Long> userIds
    );

    // 회원 탈퇴 시 사용자의 모든 알림 신청을 일괄 삭제한다.
    @Modifying
    @Query("""
delete from FestivalNotification notification
where notification.user.id = :userId
""")
    void deleteAllByUserId(@Param("userId") Long userId);
}
