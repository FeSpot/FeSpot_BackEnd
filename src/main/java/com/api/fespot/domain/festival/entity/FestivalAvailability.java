package com.api.fespot.domain.festival.entity;

public enum FestivalAvailability {
    AVAILABLE, //축제를 찾음
    NOT_FOUND, //API는 정상이지만 축제가 없음
    API_ERROR  // OpenAPI 호출 에러
}
