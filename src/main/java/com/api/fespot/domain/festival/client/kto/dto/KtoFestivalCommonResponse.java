package com.api.fespot.domain.festival.client.kto.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 한국관광공사 detailCommon2 응답을 읽는 DTO
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KtoFestivalCommonResponse(
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

        public List<CommonItem> itemList() {
            return items == null ? List.of() : items.item();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<CommonItem> item
    ) {

        public Items {
            item = item == null ? List.of() : List.copyOf(item);
        }
    }

    /**
     * 축제명, 주소, 이미지처럼 모든 관광 콘텐츠가 공통으로 가지는 정보
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommonItem(
            String contentid,
            String contenttypeid,
            String title,
            String addr1,
            String addr2,
            String firstimage,
            String firstimage2,
            @JsonProperty("cpyrhtDivCd") String imageCopyrightType
    ) {
    }
}
