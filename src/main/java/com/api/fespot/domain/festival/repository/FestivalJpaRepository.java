package com.api.fespot.domain.festival.repository;

import com.api.fespot.domain.festival.entity.Festival;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalJpaRepository extends JpaRepository<Festival, Long> {

    Optional<Festival> findByContentIdAndYear(String contentId, int year);
}
