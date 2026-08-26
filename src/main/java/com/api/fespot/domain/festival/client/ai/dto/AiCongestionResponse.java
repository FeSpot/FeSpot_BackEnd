package com.api.fespot.domain.festival.client.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiCongestionResponse(
        Double score,
        String level
) {
}
