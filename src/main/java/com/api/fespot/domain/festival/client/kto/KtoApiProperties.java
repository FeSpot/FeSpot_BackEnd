package com.api.fespot.domain.festival.client.kto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kto.api")
public class KtoApiProperties {

    private String baseUrl = "https://apis.data.go.kr/B551011/KorService2";
    private String serviceKey;
    private String mobileOs = "ETC";
    private String mobileApp = "FeSpot";
}
