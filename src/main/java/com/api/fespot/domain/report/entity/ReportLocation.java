package com.api.fespot.domain.report.entity;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "report_locations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportLocation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_location_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "festival_id", nullable = false)
    private Festival festival;

    @Column(name = "signature_name")
    private String signatureName;

    @Column(name = "anchor_latitude", nullable = false)
    private double anchorLatitude;

    @Column(name = "anchor_longitude", nullable = false)
    private double anchorLongitude;

    @Column(nullable = false)
    private LocalDateTime lastReportedAt;

    private ReportLocation(
            Festival festival,
            String signatureName,
            double anchorLatitude,
            double anchorLongitude,
            LocalDateTime lastReportedAt
    ) {
        this.festival = festival;
        this.signatureName = signatureName;
        this.anchorLatitude = anchorLatitude;
        this.anchorLongitude = anchorLongitude;
        this.lastReportedAt = lastReportedAt;
    }

    public static ReportLocation create(
            Festival festival,
            String signatureName,
            double anchorLatitude,
            double anchorLongitude
    ) {
        return new ReportLocation(festival, signatureName, anchorLatitude, anchorLongitude, LocalDateTime.now());
    }

    public void merged() {
        this.lastReportedAt = LocalDateTime.now();
    }
}
