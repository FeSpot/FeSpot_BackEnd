package com.api.fespot.domain.festival.web.dto;

import com.api.fespot.domain.festival.entity.FestivalAvailability;
import com.api.fespot.domain.festival.entity.FestivalKey;
import com.api.fespot.domain.festival.entity.FestivalSummaryReadModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FestivalHomeRes(
        FestivalKey festivalKey,
        String displayName,
        FestivalAvailability availability,
        FestivalInfo festival
) {

    public static FestivalHomeRes available(
            FestivalKey festivalKey,
            FestivalSummaryReadModel festival
    ) {
        return new FestivalHomeRes(
                festivalKey,
                festivalKey.getDisplayName(),
                FestivalAvailability.AVAILABLE,
                FestivalInfo.from(festival));
    }

    public static FestivalHomeRes unavailable(
            FestivalKey festivalKey,
            FestivalAvailability availability
    ) {
        return new FestivalHomeRes(
                festivalKey,
                festivalKey.getDisplayName(),
                availability,
                null);
    }

    public record FestivalInfo(
            String contentId, //한국관광공사에서 부여한 콘텐츠 고유 ID
            String name, //OpenAPI에 등록된 실제 축제명
            String address, //addr1, addr2를 합친 실제 주소
            LocalDate startDate, //축제 시작일
            LocalDate endDate, //축제 종료일
            String imageUrl, //축제 대표 이미지 URL
            String imageCopyrightType,//OpenAPI의 이미지 저작권 분류 코드
            String source, //정보 출처
            Instant fetchedAt //서버가 OpenAPI에서 데이터를 가져온 시각
    ) {

        private static FestivalInfo from(FestivalSummaryReadModel festival) {
            return new FestivalInfo(
                    festival.contentId(),
                    festival.name(),
                    festival.address(),
                    festival.startDate(),
                    festival.endDate(),
                    festival.imageUrl(),
                    festival.imageCopyrightType(),
                    festival.source(),
                    festival.fetchedAt());
        }
    }
}
