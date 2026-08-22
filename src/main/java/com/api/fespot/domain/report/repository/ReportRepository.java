package com.api.fespot.domain.report.repository;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.report.entity.Report;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
}
