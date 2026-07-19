package com.api.fespot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class FeSpotApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeSpotApplication.class, args);
    }
}
