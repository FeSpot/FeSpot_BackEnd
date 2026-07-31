package com.api.fespot.domain.festival.client.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.api")
public class AiApiProperties {

    private String baseUrl;
}
