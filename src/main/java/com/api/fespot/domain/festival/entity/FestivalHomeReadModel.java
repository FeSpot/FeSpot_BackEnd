package com.api.fespot.domain.festival.entity;

public record FestivalHomeReadModel(
        FestivalKey festivalKey,
        FestivalAvailability availability,
        FestivalSummaryReadModel festival
) {

    public static FestivalHomeReadModel available(
            FestivalKey festivalKey,
            FestivalSummaryReadModel festival
    ) {
        return new FestivalHomeReadModel(
                festivalKey,
                FestivalAvailability.AVAILABLE,
                festival);
    }

    public static FestivalHomeReadModel unavailable(
            FestivalKey festivalKey,
            FestivalAvailability availability
    ) {
        return new FestivalHomeReadModel(festivalKey, availability, null);
    }
}
