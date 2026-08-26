package com.api.fespot.domain.festival.client.kto.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

//OpenAPI의 외부 JSON을 읽는 DTO
@JsonIgnoreProperties(ignoreUnknown = true)
public record KtoFestivalResponse(
        Response response,
        Header header,
        Body body
) {

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

    //JSON 헤더 resultCode가 0000이면 정상응답으로 판단
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {
    }

    //JSON 바디
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            Integer totalCount,
            Items items,
            Integer numOfRows,
            Integer pageNo
    ) {
        //OpenAPI가 축제 여러개를 전달해 줄 수 있으니 List로 받는다
        public List<Item> itemList() {
            return items == null ? List.of() : items.item();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<Item> item
    ) {

        public Items {
            item = item == null ? List.of() : List.copyOf(item);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String contentid,
            String title,
            String addr1,
            String addr2,
            String eventstartdate,
            String eventenddate,
            String firstimage,
            String firstimage2,
            @JsonProperty("cpyrhtDivCd") String imageCopyrightType
    ) {
    }
}
