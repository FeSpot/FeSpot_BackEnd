package com.api.fespot.domain.festival.repository;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.festival.entity.FestivalLike;
import com.api.fespot.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalLikeRepository extends JpaRepository<FestivalLike, Long> {

    Optional<FestivalLike> findByUserAndFestival(User user, Festival festival);
}
