package com.api.fespot.domain.festival.repository;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalNotification;
import com.api.fespot.domain.user.entity.User;
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

    // 회원 탈퇴 시 사용자의 모든 알림 신청을 일괄 삭제한다.
    @Modifying
    @Query("""
delete from FestivalNotification notification
where notification.user.id = :userId
""")
    void deleteAllByUserId(@Param("userId") Long userId);
}
