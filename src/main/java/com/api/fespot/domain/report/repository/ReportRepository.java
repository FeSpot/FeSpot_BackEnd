package com.api.fespot.domain.report.repository;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.report.entity.Report;
import com.api.fespot.domain.user.entity.User;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
    List<Report> findCursorPageByUser(@Param("user") User user, @Param("cursor") Long cursor, Limit limit);

    Optional<Report> findByUserAndId(User user, Long id);

    @Query("""
            select report.id
            from Report report
            where report.createdAt between :activeSince and :now
              and report.popularNotificationAttemptedAt is null
              and (:cursor is null or report.id < :cursor)
              and (select count(reportLike.id)
                   from ReportLike reportLike
                   where reportLike.report = report) >= :minimumLikeCount
              and exists (
                  select notification.id
                  from FestivalNotification notification
                  where notification.festival = report.reportLocation.festival
                    and exists (
                        select pushToken.id
                        from PushToken pushToken
                        where pushToken.user = notification.user
                    )
              )
            order by report.id desc
            """)
    List<Long> findPopularNotificationTargets(
            @Param("activeSince") LocalDateTime activeSince,
            @Param("now") LocalDateTime now,
            @Param("minimumLikeCount") long minimumLikeCount,
            @Param("cursor") Long cursor,
            Limit limit
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Report report
            set report.popularNotificationAttemptedAt = :attemptedAt
            where report.id = :reportId
              and report.createdAt between :activeSince and :attemptedAt
              and report.popularNotificationAttemptedAt is null
              and (select count(reportLike.id)
                   from ReportLike reportLike
                   where reportLike.report = report) >= :minimumLikeCount
              and exists (
                  select notification.id
                  from FestivalNotification notification
                  where notification.festival = report.reportLocation.festival
                    and exists (
                        select pushToken.id
                        from PushToken pushToken
                        where pushToken.user = notification.user
                    )
              )
            """)
    int markPopularNotificationAttemptedIfEligible(
            @Param("reportId") Long reportId,
            @Param("activeSince") LocalDateTime activeSince,
            @Param("attemptedAt") LocalDateTime attemptedAt,
            @Param("minimumLikeCount") long minimumLikeCount
    );

    @Query("""
            select count(report) > 0
            from Report report
            where report.id = :reportId
              and report.createdAt between :activeSince and :now
              and (select count(reportLike.id)
                   from ReportLike reportLike
                   where reportLike.report = report) >= :minimumLikeCount
            """)
    boolean isPopularNotificationEligible(
            @Param("reportId") Long reportId,
            @Param("activeSince") LocalDateTime activeSince,
            @Param("now") LocalDateTime now,
            @Param("minimumLikeCount") long minimumLikeCount
    );

    List<Report> findAllByCreatedAtBefore(LocalDateTime threshold);

    @Modifying
    @Query("""
delete from Report r
where r.reportLocation.id in :reportLocationIds
""")
    void deleteAllByReportLocationIdIn(
            @Param("reportLocationIds") List<Long> reportLocationIds);

    @Query("""
select r.imageUrl from Report r
where r.reportLocation.id = :reportLocationId and r.imageUrl is not null
""")
    List<String> findImageUrlsByReportLocationId
            (@Param("reportLocationId") Long reportLocationId
            );
}
