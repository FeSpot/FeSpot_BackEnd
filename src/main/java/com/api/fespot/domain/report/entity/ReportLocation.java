package com.api.fespot.domain.report.entity;

import com.api.fespot.domain.festival.entity.Festival;
import com.api.fespot.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "anchor_latitude", nullable = false)
    private double anchorLatitude;

    @Column(name = "anchor_longitude", nullable = false)
    private double anchorLongitude;

    private ReportLocation(
            Festival festival,
            double anchorLatitude,
            double anchorLongitude
    ) {
        this.festival = festival;
        this.anchorLatitude = anchorLatitude;
        this.anchorLongitude = anchorLongitude;
    }

    public static ReportLocation create(
            Festival festival,
            double anchorLatitude,
            double anchorLongitude
    ) {
        return new ReportLocation(festival, anchorLatitude, anchorLongitude);
    }
}
