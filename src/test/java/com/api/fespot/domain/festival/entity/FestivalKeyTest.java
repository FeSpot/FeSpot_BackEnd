package com.api.fespot.domain.festival.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FestivalKeyTest {

    @Test
    void providesAiFestivalId() {
        assertThat(FestivalKey.BUSAN_INTERNATIONAL_FILM_FESTIVAL
                .getAiFestivalId())
                .isEqualTo("busan_film_festival");
    }
}
