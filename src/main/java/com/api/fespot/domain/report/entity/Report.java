package com.api.fespot.domain.report.entity;

import com.api.fespot.domain.user.entity.User;
import com.api.fespot.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_location_id", nullable = false)
    private ReportLocation reportLocation;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "original_location", nullable = false, length = 100)
    private String originalLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "congestion_level", nullable = false)
    private CongestionLevel congestionLevel;

    @Column(name = "image_url")
    private String imageUrl;

    @Builder
    private Report(
            User user,
            ReportLocation reportLocation,
            String content,
            String originalLocation,
            CongestionLevel congestionLevel,
            String imageUrl
    ) {
        this.user = user;
        this.reportLocation = reportLocation;
        this.content = content;
        this.originalLocation = originalLocation;
        this.congestionLevel = congestionLevel;
        this.imageUrl = imageUrl;
    }

    public void update(String imageUrl, String content, CongestionLevel congestionLevel){
        if(imageUrl != null){
            this.imageUrl = imageUrl;
        }
        if(content != null){
            this.content = content;
        }
        if(congestionLevel != null){
            this.congestionLevel = congestionLevel;
        }
    }
}
