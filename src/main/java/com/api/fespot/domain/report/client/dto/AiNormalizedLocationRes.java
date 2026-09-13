package com.api.fespot.domain.report.client.dto;

public record AiNormalizedLocationRes(
        String normalized_location,
        boolean location_matched
) {
}
