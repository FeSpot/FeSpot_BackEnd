package com.api.fespot.domain.festival.client.kto.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 한국관광공사 detailIntro2의 축제 소개정보 응답을 읽는 DTO
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KtoFestivalIntroResponse(
        Response response,
        Header header,
        Body body
) {

    /**
     * 실제 API의 포장 응답과 Swagger의 직접 응답을 같은 형태로 반환한다.
     */
    public Response payload() {
        if (response != null) {
            return response;
        }
        if (header == null && body == null) {
            return null;
        }
        return new Response(header, body);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            Integer totalCount,
            Items items
    ) {

        public List<IntroItem> itemList() {
            return items == null ? List.of() : items.item();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<IntroItem> item
    ) {

        public Items {
            item = item == null ? List.of() : List.copyOf(item);
        }
    }

    /**
     * 축제의 행사장, 기간, 운영시간 정보
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IntroItem(
            String contentid,
            String contenttypeid,
            String eventplace,
            String eventstartdate,
            String eventenddate,
            String playtime
    ) {
    }
}
