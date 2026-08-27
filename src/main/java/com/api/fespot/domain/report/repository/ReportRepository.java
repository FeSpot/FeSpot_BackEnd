package com.api.fespot.domain.report.repository;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.user.entity.User;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @Query("""
            select report
            from Report report
            join fetch report.reportLocation location
            where location.festival = :festival
              and (:cursor is null or report.id < :cursor)
            order by report.id desc
            """)
    List<Report> findFeedByFestival(
            @Param("festival") Festival festival,
            @Param("cursor") Long cursor,
            Limit limit
    );


    @Query("""
            select report
            from Report report join fetch report.reportLocation location
            where location.festival = :festival
             and report.createdAt >= :activeSince
            order by report.createdAt desc""")
    List<Report> findActiveByFestival(@Param("festival") Festival festival, @Param("activeSince") LocalDateTime activeSince);


    @Query("""
            select report
            from Report report
            where report.user = :user and (:cursor is null or report.id < :cursor)
            order by report.id desc
            """)
    List<Report> findByUser(@Param("user") User user, @Param("cursor") Long cursor, Limit limit);

    Optional<Report> findByUserAndId(User user, Long id);
}
