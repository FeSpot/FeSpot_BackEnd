package com.api.fespot.domain.festival.repository;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalNotification;
import com.api.fespot.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalNotificationRepository
        extends JpaRepository<FestivalNotification, Long> {

    Optional<FestivalNotification> findByUserAndFestival(
            User user,
            Festival festival
    );
}
