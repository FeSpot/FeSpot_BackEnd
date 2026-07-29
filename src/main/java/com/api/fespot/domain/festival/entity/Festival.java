package com.api.fespot.domain.festival.entity;

import com.api.fespot.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 기능의 관계를 연결하기 위한 축제 식별자만 저장한다.
 * 한국관광공사 OpenAPI의 축제 상세정보는 저장하지 않는다.
 */
@Getter
@Entity
@Table(
        name = "festivals",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_festival_contents_id_year",
                        columnNames = {"contents_id", "year"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Festival extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "festival_id")
    private Long id;

    @Column(name = "contents_id", nullable = false, length = 64)
    private String contentId;

    @Column(nullable = false)
    private int year;

    private Festival(String contentId, int year) {
        this.contentId = contentId;
        this.year = year;
    }

    public static Festival create(String contentId, int year) {
        return new Festival(contentId, year);
    }
}
