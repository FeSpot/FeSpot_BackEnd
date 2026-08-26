package com.api.fespot.domain.plan.web.dto;

import com.api.fespot.domain.plan.entity.VisitPlace;

import java.time.LocalTime;

record PlaceBlock(
        Long placeId,
        LocalTime startAt,
        String placeName
) {
    static PlaceBlock convertToPlaceBlock(VisitPlace place) {
        return new PlaceBlock(
                place.getId(),
                place.getStartAt(),
                place.getPlaceName()
        );
    }
}
