package com.api.fespot.domain.interest.repository;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.interest.entity.InterestFestival;
import com.api.fespot.domain.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InterestFestivalRepository extends JpaRepository<InterestFestival, Long> {

    boolean existsByUserAndFestival(User user, Festival festival);

    @Query("""
select i
from InterestFestival i
join fetch i.festival f
where i.user.id = :userId
    and f.year = :year
order by i.createdAt desc, i.id desc
""")
    List<InterestFestival> findFirstPageByUserId(
            @Param("userId") Long userId,
            @Param("year") int year,
            Pageable pageable);

    @Query("""
select i
from InterestFestival i
join fetch i.festival f
where i.user.id = :userId
    and f.year = :year
    and i.id < :cursorId
order by i.createdAt desc, i.id desc
""")
    List<InterestFestival> findPagedByUserId(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
