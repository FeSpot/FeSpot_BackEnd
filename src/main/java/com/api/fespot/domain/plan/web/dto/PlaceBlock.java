package com.api.fespot.domain.plan.web.dto;

import com.api.fespot.domain.plan.entity.VisitPlace;
import com.api.fespot.domain.plan.entity.enums.PlaceType;

import java.time.LocalTime;

record PlaceBlock(
        Long placeId,
        LocalTime startAt,
        PlaceType placeType,
        String placeName
) {
    static PlaceBlock convertToPlaceBlock(VisitPlace place) {
        return new PlaceBlock(
                place.getId(),
                place.getStartAt(),
                place.getPlaceType(),
                place.getPlaceName()
        );
    }
}
