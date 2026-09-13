package com.api.fespot.domain.report.repository;

import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.report.entity.ReportLike;
import com.api.fespot.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportLikeRepository extends JpaRepository<ReportLike,Long> {
    Optional<ReportLike> findByUserAndReport(User user, Report report);

    long countByReport_Id(Long reportId);

    @Query("""
            select reportLike.report.id as reportId, count(reportLike.id) as likeCount
            from ReportLike reportLike
            where reportLike.report.id in :reportIds
            group by reportLike.report.id
            """)
    List<ReportLikeCount> countByReportIds(@Param("reportIds") List<Long> reportIds);

    @Query("""
            select reportLike.report.id
            from ReportLike reportLike
            where reportLike.user.id = :userId and reportLike.report.id in :reportIds
            """)
    List<Long> findLikeReportIds(
            @Param("userId") Long userId,
            @Param("reportIds") List<Long> reportIds
    );

    @Modifying
    @Query("""
            delete from ReportLike reportLike
            where reportLike.report.id = :reportId""")
    void deleteAllByReportId(@Param("reportId")Long reportId);

    @Modifying
    @Query("""
delete from ReportLike reportLike
where reportLike.report.reportLocation.id in :reportLocationIds
""")
    void deleteAllByReportLocationIdIn(@Param("reportLocationIds") List<Long> reportLocationIds);
}
