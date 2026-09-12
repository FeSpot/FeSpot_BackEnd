package com.api.fespot.domain.report.repository;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.domain.report.entity.ReportLocation;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportLocationRepository extends JpaRepository<ReportLocation, Long> {

    @Query("""
            select distinct report.reportLocation
            from Report report
            where report.reportLocation.festival = :festival
              and report.createdAt >= :activeSince
            """)
    List<ReportLocation> findActiveByFestival(
            @Param("festival") Festival festival,
            @Param("activeSince") LocalDateTime activeSince
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            update ReportLocation location
            set location.signatureName = :signatureName
            where location.id = :id
              and location.signatureName is null
            """)
    int updateSignatureNameIfAbsent(
            @Param("id") Long id,
            @Param("signatureName") String signatureName
    );
}
